package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaRouteToolItem;
import faygolover.zoneartifacts.network.TeslaRouteSyncHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
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
 * All click handling for {@link TeslaRouteToolItem} - rebuilt around one rule: a click either lands
 * on a real, ordinary block (placing a <em>new</em> point) or on an existing point's own {@link
 * TeslaWaypointEntity} marker (acting on that <em>existing</em> point), and which one it is is
 * always resolved by Minecraft's own exact, single-target interaction system - never by raytracing
 * an area, inflating a hitbox, or otherwise guessing what the player probably meant.
 * <p>
 * New point: {@link #onRightClickBlock} places it one block off the clicked face (see {@link
 * #placedPos}), so the point itself - and later, the Tesla that ends up there - is never embedded in
 * the solid block that was clicked to aim it. A {@link TeslaWaypointEntity} marker is spawned there
 * immediately (see {@link TeslaWaypointMarkers}), which is what makes it clickable as an
 * <em>existing</em> point from then on.
 * <p>
 * Existing point: {@link #onEntityInteract} (right-click - continues/closes) and {@link
 * #onAttackEntity} (left-click - removes) fire only for an actual {@link TeslaWaypointEntity}, so
 * there is nothing to resolve at all; the entity clicked <em>is</em> the point.
 * <p>
 * The state machine itself (unchanged from spec):
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
 *       route (and every one of its markers).</li>
 *   <li>Left-click a point of the chain being built -> removes just that point and its marker (the
 *       list closing back up around the gap on its own), unless it's the chain's start, which scraps
 *       the whole chain (and all its markers) instead.</li>
 *   <li>Left-click a point belonging to a different, already-finished route while building -> no-op.</li>
 *   <li>Left-click a plain block that's part of no route -> nothing happens.</li>
 * </ol>
 * Every message that does get written also names the current chain's start-point coordinates, so a
 * GM who wanders off mid-build can always tell where to click to cancel or close it.
 * <p>
 * Forge fires both {@code RightClickBlock} and {@code EntityInteractSpecific} once per hand -
 * including an empty off-hand - and the two firings for the same physical click can land in the very
 * same server tick (see e.g. MinecraftForge issue #5508). {@link #firstOfTick} sidesteps this: it
 * remembers the last {@code (tick, pos)} this player's click logic actually ran for, separately for
 * left- and right-click, and skips anything that matches it again.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class TeslaRouteInteractionHandler {

    private record ClickKey(long gameTime, BlockPos pos) {
    }

    private static final Map<UUID, ClickKey> lastRightClick = new HashMap<>();
    private static final Map<UUID, ClickKey> lastLeftClick = new HashMap<>();

    // ---- entry points ---------------------------------------------------

    /** A real block, clicked to place a brand new point - see the class javadoc for why the point
     *  itself lands one block off the clicked face rather than on the block clicked. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (TeslaRouteToolItem.heldStack(player) == null) return;

        if (event.isCancelable()) {
            event.setCanceled(true);
        }

        BlockPos pos = placedPos(event.getPos(), event.getFace());
        handleRightClick(level, player, pos);
    }

    /** A real block, left-clicked - per rule 9 this never does anything on its own (an existing
     *  point is only ever removed by clicking its {@link TeslaWaypointEntity} marker directly, see
     *  {@link #onAttackEntity}), but the block-break itself is still cancelled while the tool is
     *  held, same as right-click. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (TeslaRouteToolItem.heldStack(player) == null) return;
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    /** An existing point's own marker, right-clicked - rules 3, 4 and 5. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (TeslaRouteToolItem.heldStack(player) == null) return;
        if (!(event.getTarget() instanceof TeslaWaypointEntity marker)) return;

        if (event.isCancelable()) {
            event.setCanceled(true);
        }
        handleRightClick(level, player, marker.waypointPos());
    }

    /** An existing point's own marker, left-clicked (attacked) - rules 6, 7 and 8. Attacking is
     *  inherently main-hand-only in vanilla, so unlike the two right-click paths above this one was
     *  never at risk of double-firing per hand - the {@link #firstOfTick} guard still applies for
     *  uniformity, not because it's been observed to be needed here. */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (TeslaRouteToolItem.heldStack(player) == null) return;
        if (!(event.getTarget() instanceof TeslaWaypointEntity marker)) return;

        event.setCanceled(true);
        handleLeftClick(level, player, marker.waypointPos());
    }

    /** The point one block off {@code clickedPos} in the direction of {@code clickedFace} - the
     *  same "on top of / beside the block you clicked" spot vanilla itself places a torch or sign
     *  at, chosen specifically so a freshly placed point (and, once the route closes, the Tesla that
     *  spawns there) is never sitting inside the solid block that was clicked to place it. */
    private static BlockPos placedPos(BlockPos clickedPos, Direction clickedFace) {
        return clickedPos.relative(clickedFace).immutable();
    }

    // ---- state machine ----------------------------------------------------

    /** Right-click state machine (rules 1, 2, 3, 4, 5 above). */
    private static void handleRightClick(ServerLevel level, Player player, BlockPos pos) {
        ItemStack stack = TeslaRouteToolItem.heldStack(player);
        if (stack == null) return;
        if (!firstOfTick(lastRightClick, player, level, pos)) return;

        ResourceLocation typeId = ((TeslaRouteToolItem) stack.getItem()).teslaTypeId();
        List<BlockPos> chain = new ArrayList<>(TeslaRouteToolItem.getChain(stack));

        if (chain.isEmpty()) {
            chain.add(pos);
            TeslaRouteToolItem.setChain(stack, chain);
            TeslaWaypointMarkers.spawn(level, pos);
            notify(player, "Начало: ", pos);
            return;
        }

        BlockPos start = chain.get(0);

        if (pos.equals(start)) {
            TeslaRoute route = TeslaSavedData.get(level).createRoute(level, typeId, List.copyOf(chain));
            TeslaRouteToolItem.clearChain(stack);
            TeslaRouteSyncHandler.broadcast(level);
            notify(player, "Маршрут #" + route.id() + " готов (" + chain.size() + " т.): ", start);
            return;
        }

        if (chain.contains(pos)) {
            notify(player, "Уже отмечена. Начало: ", start);
            return;
        }

        TeslaRoute otherRoute = TeslaSavedData.get(level).routeContaining(pos);
        if (otherRoute != null) {
            notify(player, "Занято маршрутом #" + otherRoute.id() + ". Начало: ", start);
            return;
        }

        chain.add(pos);
        TeslaRouteToolItem.setChain(stack, chain);
        TeslaWaypointMarkers.spawn(level, pos);
        notify(player, "Точка " + chain.size() + ": ", pos, ". Начало: ", start);
    }

    /** Left-click state machine (rules 6, 7, 8, 9 above). */
    private static void handleLeftClick(ServerLevel level, Player player, BlockPos pos) {
        ItemStack stack = TeslaRouteToolItem.heldStack(player);
        if (stack == null) return;
        if (!firstOfTick(lastLeftClick, player, level, pos)) return;

        List<BlockPos> chain = new ArrayList<>(TeslaRouteToolItem.getChain(stack));

        if (chain.isEmpty()) {
            TeslaRoute route = TeslaSavedData.get(level).routeContaining(pos);
            if (route != null) {
                int waypointCount = route.waypoints().size();
                TeslaSavedData.get(level).removeRoute(level, route.id());
                TeslaWaypointMarkers.discardAll(level, route.waypoints());
                TeslaRouteSyncHandler.broadcast(level);
                notify(player, "Маршрут #" + route.id() + " удалён (" + waypointCount + " т.)");
            }
            // A marker with no chain and no route under it can't exist - nothing else to do here.
            return;
        }

        BlockPos start = chain.get(0);
        int index = chain.indexOf(pos);
        if (index >= 0) {
            if (index == 0) {
                TeslaWaypointMarkers.discardAll(level, chain);
                TeslaRouteToolItem.clearChain(stack);
                notify(player, "Отменено. ", start, " свободна");
            } else {
                chain.remove(index);
                TeslaRouteToolItem.setChain(stack, chain);
                TeslaWaypointMarkers.discardAt(level, pos);
                notify(player, "Убрана ", pos, " (начало ", start, ", ост. " + chain.size() + ")");
            }
            return;
        }

        TeslaRoute otherRoute = TeslaSavedData.get(level).routeContaining(pos);
        if (otherRoute != null) {
            notify(player, "Занято маршрутом #" + otherRoute.id() + ". Начало: ", start);
        }
        // A marker while building, belonging to neither the current chain nor any finished route,
        // can't exist - nothing else to do here.
    }

    /** True the first time this {@code (gameTime, pos)} pair is seen for this player's right- or
     *  left-click logic (whichever map is passed in); false for every repeat of the exact same pair
     *  — see the class javadoc for why a single physical click needs this. */
    private static boolean firstOfTick(Map<UUID, ClickKey> tracker, Player player, ServerLevel level, BlockPos pos) {
        ClickKey key = new ClickKey(level.getGameTime(), pos);
        ClickKey previous = tracker.put(player.getUUID(), key);
        return !key.equals(previous);
    }

    // ---- cleanup ----------------------------------------------------------

    /** Clears any in-progress (not yet finished) chain - and discards its markers - from every route
     *  tool in the player's whole inventory, not just their hands - called on logout, dimension
     *  change, and server shutdown so an abandoned build never lingers, and by {@code
     *  /fl_zone_arts tesla_reset_chains} on demand. A finished, persisted {@link TeslaRoute} is never
     *  touched by this. Returns whether anything was actually cleared, purely for that command's own
     *  feedback message. */
    public static boolean clearAllChains(ServerLevel level, Player player) {
        boolean cleared = false;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof TeslaRouteToolItem) {
                List<BlockPos> chain = TeslaRouteToolItem.getChain(stack);
                if (!chain.isEmpty()) {
                    TeslaWaypointMarkers.discardAll(level, chain);
                    TeslaRouteToolItem.clearChain(stack);
                    cleared = true;
                }
            }
        }
        return cleared;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            clearAllChains(level, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            clearAllChains(level, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.level() instanceof ServerLevel level) {
                clearAllChains(level, player);
            }
        }
    }

    // ---- messaging ----------------------------------------------------------

    /** Builds and sends a short actionbar message. Each {@code parts} entry is either a plain
     *  {@code String} appended as-is, or a {@link BlockPos} appended highlighted in color - the
     *  coordinate colorizing every message that names one is built for here, in one place, rather
     *  than in every call site. */
    private static void notify(Player player, Object... parts) {
        MutableComponent message = Component.literal("fl_zone_arts: ");
        for (Object part : parts) {
            if (part instanceof BlockPos pos) {
                message.append(Component.literal(pos.toShortString()).withStyle(ChatFormatting.AQUA));
            } else {
                message.append(Component.literal(String.valueOf(part)));
            }
        }
        player.displayClientMessage(message, true);
    }
}
