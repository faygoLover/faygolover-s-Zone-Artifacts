package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaRouteToolItem;
import faygolover.zoneartifacts.network.TeslaRouteSyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * All click handling for {@link TeslaRouteToolItem}, entirely server-side. Unlike Electra's placer
 * (whose zones can float off the block grid, forcing the left-click/remove case to start
 * client-side — see {@code ClientAnomalyInputHandler}), every Tesla waypoint is a real block, so
 * both {@code RightClickBlock} and {@code LeftClickBlock} already carry the exact {@link BlockPos}
 * clicked and reach the server reliably on their own — no client-side raytrace needed.
 * <p>
 * Right-click builds up an in-progress chain (stored in the held stack's own NBT — see {@link
 * TeslaRouteToolItem#getChain}): each click on a fresh block appends it; clicking the chain's own
 * first point again, once it has at least two points, closes the loop into a real, persisted
 * {@link TeslaRoute}; clicking any other point already in the chain scraps the whole in-progress
 * chain instead. With no chain in progress, right-clicking a waypoint that already belongs to a
 * finished route removes that entire route. Left-click never breaks a block while this tool is
 * held — it's purely a status readout for whatever's under the cursor.
 * <p>
 * Forge fires {@code RightClickBlock} once per hand — including the off-hand, even when it's
 * empty (a well-known quirk: see e.g. MinecraftForge issue #5508) — so without a guard, a single
 * physical click would run this whole method twice in the same tick: once adding a point, and
 * once immediately after seeing that same point already in the chain and resetting it. {@link
 * #isPrimaryFiring} filters that down to exactly one pass per click.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class TeslaRouteInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;

        ItemStack stack = TeslaRouteToolItem.heldStack(player);
        if (stack == null || !isPrimaryFiring(player, event.getHand())) return;
        ResourceLocation typeId = ((TeslaRouteToolItem) stack.getItem()).teslaTypeId();

        if (event.isCancelable()) {
            event.setCanceled(true);
        }

        BlockPos pos = event.getPos().immutable();
        List<BlockPos> chain = new ArrayList<>(TeslaRouteToolItem.getChain(stack));

        if (chain.isEmpty()) {
            TeslaRoute existing = TeslaSavedData.get(level).routeContaining(pos);
            if (existing != null) {
                int waypointCount = existing.waypoints().size();
                TeslaSavedData.get(level).removeRoute(level, existing.id());
                TeslaRouteSyncHandler.broadcast(level);
                notify(player, "маршрут #" + existing.id() + " удалён (точек: " + waypointCount + ")");
            } else {
                chain.add(pos);
                TeslaRouteToolItem.setChain(stack, chain);
                notify(player, "точка 1 установлена в " + pos.toShortString()
                        + " - продолжайте ПКМ по блокам, затем кликните по этой же точке ещё раз, чтобы замкнуть маршрут");
            }
            return;
        }

        if (pos.equals(chain.get(0)) && chain.size() >= 2) {
            TeslaSavedData.get(level).createRoute(level, typeId, List.copyOf(chain));
            TeslaRouteToolItem.clearChain(stack);
            TeslaRouteSyncHandler.broadcast(level);
            notify(player, "маршрут создан (точек: " + chain.size() + ") - Тесла уже патрулирует его");
            return;
        }

        if (chain.contains(pos)) {
            TeslaRouteToolItem.clearChain(stack);
            notify(player, "цепочка сброшена (эта точка уже есть в цепочке)");
            return;
        }

        chain.add(pos);
        TeslaRouteToolItem.setChain(stack, chain);
        notify(player, "точка " + chain.size() + " установлена в " + pos.toShortString());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;

        ItemStack stack = TeslaRouteToolItem.heldStack(player);
        if (stack == null) return;

        if (event.isCancelable()) {
            event.setCanceled(true);
        }

        BlockPos pos = event.getPos().immutable();
        List<BlockPos> chain = TeslaRouteToolItem.getChain(stack);
        int chainIndex = chain.indexOf(pos);
        if (chainIndex >= 0) {
            notify(player, "точка занята (точка №" + (chainIndex + 1) + " текущей цепочки)");
            return;
        }

        TeslaRoute route = TeslaSavedData.get(level).routeContaining(pos);
        if (route != null) {
            notify(player, "точка занята (принадлежит маршруту #" + route.id() + ")");
        }
    }

    /** True only for the hand that actually holds the tool — see the class javadoc for why this
     *  guard exists. {@code RightClickBlock} isn't fired per-hand for {@code LeftClickBlock}
     *  (breaking is always main-hand-only), so no equivalent guard is needed there. */
    private static boolean isPrimaryFiring(Player player, InteractionHand hand) {
        boolean toolInMainHand = player.getMainHandItem().getItem() instanceof TeslaRouteToolItem;
        return hand == (toolInMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    private static void notify(Player player, String message) {
        player.displayClientMessage(Component.literal("fl_zone_arts: " + message), true);
    }
}
