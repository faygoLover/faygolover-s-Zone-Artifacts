package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaRouteToolItem;
import faygolover.zoneartifacts.network.TeslaRouteSyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All click handling for {@link TeslaRouteToolItem}. The actual point-by-point state machine lives
 * in {@link #handleRightClick} / {@link #handleLeftClick}, both {@code public static} so the same
 * logic can be driven two ways per click type: directly from {@link PlayerInteractEvent.RightClickBlock}
 * / {@code LeftClickBlock} below when the click lands on a real block, or — once a route is finished
 * and its marker blocks get broken (a Tesla bumps into them until the GM clears them; see the route
 * tool's own item description), leaving a waypoint floating in open air that those two events can
 * never see — through a fallback specific to each click type. Right-click's fallback,
 * {@link #onRightClickItem} below, runs fully server-side via {@code TeslaRouteTargeting}, since
 * {@code RightClickItem} reaches the server directly. Left-click's fallback needs one extra step:
 * {@code LeftClickEmpty} is a client-only event, so {@code ClientTeslaRouteInputHandler} resolves the
 * target client-side (see {@code TeslaRouteClientTargeting}) and sends {@code TeslaRouteClickPacket}
 * for the server to act on. Exactly the same split Electra's placer already uses (compare
 * {@code AnomalyInteractionHandler} + {@code ClientAnomalyInputHandler}).
 * <p>
 * The full state machine, as specified:
 * <ol>
 *   <li>Right-click a block with no chain in progress -> starts a new chain there.</li>
 *   <li>Right-click a block with a chain in progress -> appends it, announcing its number in this
 *       chain.</li>
 *   <li>Right-click a point already in the chain, other than its start -> no-op.</li>
 *   <li>Right-click a point belonging to a different, already-finished route while building -> no-op.</li>
 *   <li>Right-click the chain's own start point -> finishes the route right there, however many
 *       points it has (even just the one) -> a real, persisted {@link TeslaRoute}, and its Tesla
 *       starts patrolling immediately.</li>
 *   <li>Left-click any point of a finished route, with no chain in progress -> deletes that whole
 *       route.</li>
 *   <li>Left-click a point of the chain being built -> removes just that point (the list closing
 *       back up around the gap on its own), unless it's the chain's start, which scraps the whole
 *       chain instead.</li>
 *   <li>Left-click a point belonging to a different, already-finished route while building -> no-op.</li>
 *   <li>Left-click a plain block that's part of no route -> nothing happens.</li>
 * </ol>
 * Every message that does get written also names the current chain's start-point coordinates, so a
 * GM who wanders off mid-build can always tell where to click to cancel or close it.
 * <p>
 * Forge fires both {@code RightClickBlock} and {@code RightClickItem} once per hand — including an
 * empty off-hand — and the two firings for the same physical click can land in the very same server
 * tick (see e.g. MinecraftForge issue #5508); a per-hand filter turned out not to reliably tell them
 * apart. {@link #firstOfTick} sidesteps the question of *why* entirely: it just remembers the last
 * {@code (tick, pos)} this player's click logic actually ran for, separately for left- and
 * right-click, and skips anything that matches it again — regardless of which event (or which of the
 * two fallback paths above) it came in through.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class TeslaRouteInteractionHandler {

    private record ClickKey(long gameTime, BlockPos pos) {
    }

    private static final Map<UUID, ClickKey> lastRightClick = new HashMap<>();
    private static final Map<UUID, ClickKey> lastLeftClick = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (TeslaRouteToolItem.heldStack(player) == null) return;

        if (event.isCancelable()) {
            event.setCanceled(true);
        }
        handleRightClick(level, player, event.getPos().immutable());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (TeslaRouteToolItem.heldStack(player) == null) return;

        if (event.isCancelable()) {
            event.setCanceled(true);
        }
        handleLeftClick(level, player, event.getPos().immutable());
    }

    /** The right-click counterpart of {@code ClientTeslaRouteInputHandler}'s left-click fallback:
     *  fires whenever there's no real block under the cursor at all (a floating, already-uncovered
     *  waypoint), and — unlike left-click — reaches the server directly, so {@link
     *  TeslaRouteTargeting} can resolve the target itself with no client packet involved. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (TeslaRouteToolItem.heldStack(player) == null) return;

        TeslaRouteTargeting.pick(level, player).ifPresent(pos -> {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
            handleRightClick(level, player, pos);
        });
    }

    /** Right-click state machine (rules 1, 2, 3, 4, 5 above). Public so {@code
     *  TeslaRouteClickPacket} can drive it too. */
    public static void handleRightClick(ServerLevel level, Player player, BlockPos pos) {
        ItemStack stack = TeslaRouteToolItem.heldStack(player);
        if (stack == null) return;
        if (!firstOfTick(lastRightClick, player, level, pos)) return;

        ResourceLocation typeId = ((TeslaRouteToolItem) stack.getItem()).teslaTypeId();
        List<BlockPos> chain = new ArrayList<>(TeslaRouteToolItem.getChain(stack));

        if (chain.isEmpty()) {
            chain.add(pos);
            TeslaRouteToolItem.setChain(stack, chain);
            notify(player, "точка 1 (начальная) установлена в " + pos.toShortString()
                    + " - продолжайте ПКМ по блокам; кликните по этой же точке ещё раз, чтобы завершить маршрут");
            return;
        }

        BlockPos start = chain.get(0);

        if (pos.equals(start)) {
            TeslaRoute route = TeslaSavedData.get(level).createRoute(level, typeId, List.copyOf(chain));
            TeslaRouteToolItem.clearChain(stack);
            TeslaRouteSyncHandler.broadcast(level);
            notify(player, "маршрут #" + route.id() + " завершён (точек: " + chain.size() + ", начало в "
                    + start.toShortString() + ") - Тесла начинает патрулирование");
            return;
        }

        if (chain.contains(pos)) {
            notify(player, "эта точка уже есть в строящемся маршруте - ничего не изменилось (начало маршрута: "
                    + start.toShortString() + ")");
            return;
        }

        TeslaRoute otherRoute = TeslaSavedData.get(level).routeContaining(pos);
        if (otherRoute != null) {
            notify(player, "эта точка принадлежит другому маршруту (#" + otherRoute.id()
                    + ") - ничего не изменилось (начало текущего маршрута: " + start.toShortString() + ")");
            return;
        }

        chain.add(pos);
        TeslaRouteToolItem.setChain(stack, chain);
        notify(player, "точка " + chain.size() + " установлена в " + pos.toShortString()
                + " (начало маршрута: " + start.toShortString() + ")");
    }

    /** Left-click state machine (rules 6, 7, 8, 9 above). Public so {@code TeslaRouteClickPacket}
     *  can drive it too. */
    public static void handleLeftClick(ServerLevel level, Player player, BlockPos pos) {
        ItemStack stack = TeslaRouteToolItem.heldStack(player);
        if (stack == null) return;
        if (!firstOfTick(lastLeftClick, player, level, pos)) return;

        List<BlockPos> chain = new ArrayList<>(TeslaRouteToolItem.getChain(stack));

        if (chain.isEmpty()) {
            TeslaRoute route = TeslaSavedData.get(level).routeContaining(pos);
            if (route != null) {
                int waypointCount = route.waypoints().size();
                TeslaSavedData.get(level).removeRoute(level, route.id());
                TeslaRouteSyncHandler.broadcast(level);
                notify(player, "маршрут #" + route.id() + " удалён целиком (точек было: " + waypointCount + ")");
            }
            // Plain block, no route involved at all - nothing happens, nothing to say.
            return;
        }

        BlockPos start = chain.get(0);
        int index = chain.indexOf(pos);
        if (index >= 0) {
            if (index == 0) {
                TeslaRouteToolItem.clearChain(stack);
                notify(player, "строящийся маршрут отменён (начальная точка " + start.toShortString() + " убрана)");
            } else {
                chain.remove(index);
                TeslaRouteToolItem.setChain(stack, chain);
                notify(player, "точка " + pos.toShortString() + " убрана из маршрута (начало: "
                        + start.toShortString() + ", точек осталось: " + chain.size() + ")");
            }
            return;
        }

        TeslaRoute otherRoute = TeslaSavedData.get(level).routeContaining(pos);
        if (otherRoute != null) {
            notify(player, "эта точка принадлежит другому, уже завершённому маршруту (#" + otherRoute.id()
                    + ") - ничего не убрано (начало текущего маршрута: " + start.toShortString() + ")");
        }
        // Plain block while building - nothing happens, nothing to say.
    }

    /** True the first time this {@code (gameTime, pos)} pair is seen for this player's right- or
     *  left-click logic (whichever map is passed in); false for every repeat of the exact same pair
     *  — see the class javadoc for why a single physical click needs this. */
    private static boolean firstOfTick(Map<UUID, ClickKey> tracker, Player player, ServerLevel level, BlockPos pos) {
        ClickKey key = new ClickKey(level.getGameTime(), pos);
        ClickKey previous = tracker.put(player.getUUID(), key);
        return !key.equals(previous);
    }

    /** Clears any in-progress (not yet finished) chain from every route tool in the player's whole
     *  inventory, not just their hands - called on logout, dimension change, and server shutdown so
     *  an abandoned build never lingers. A finished, persisted {@link TeslaRoute} is untouched. */
    public static void clearAllChains(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof TeslaRouteToolItem && !TeslaRouteToolItem.getChain(stack).isEmpty()) {
                TeslaRouteToolItem.clearChain(stack);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearAllChains(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearAllChains(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            clearAllChains(player);
        }
    }

    private static void notify(Player player, String message) {
        player.displayClientMessage(Component.literal("fl_zone_arts: " + message), true);
    }
}
