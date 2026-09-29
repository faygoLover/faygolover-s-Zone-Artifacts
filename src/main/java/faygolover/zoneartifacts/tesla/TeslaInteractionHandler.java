package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaPlacerItem;
import faygolover.zoneartifacts.network.TeslaSyncHandler;
import faygolover.zoneartifacts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The full Tesla waypoint-route builder click state machine, exactly as specified:
 * <ul>
 *     <li>Right-click a plain block: no build in progress → start a new route there; a build in
 *     progress → append the next point (adjacent to the clicked face, same as any normal block
 *     placement).</li>
 *     <li>Right-click the start point of the route currently being built → finalize it (spawning
 *     Tesla immediately, even for a single-point, stationary route). Right-click any other already
 *     placed point, or a point belonging to a different route/session, is a no-op.</li>
 *     <li>Left-click any waypoint while nothing is being built → deletes that whole completed
 *     route (and its live Tesla). Left-click a point of the route currently being built → removes
 *     it (reconnecting around a middle point, shortening at the end, or discarding the whole
 *     build if it was the start point). A plain block, or a point of an unrelated route/session,
 *     is always a no-op.</li>
 *     <li>Every click, if the clicking player has a build in progress, prints its start point's
 *     coordinates — a safety net so a GM can always find and cancel it.</li>
 * </ul>
 * Targeting goes through {@link TeslaWaypointTargeting}, exactly like {@code AnomalyTargeting}
 * does for Electra, so this works the same whether the crosshair is on a real block or a waypoint
 * hanging in open air.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class TeslaInteractionHandler {

    private TeslaInteractionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TeslaPlacerItem.isTeslaPlacer(event.getItemStack())) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        handleRightClick(player, player.serverLevel(), event.getPos(), event.getFace());
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TeslaPlacerItem.isTeslaPlacer(event.getItemStack())) return;

        event.setCanceled(true);
        handleRightClick(player, player.serverLevel(), null, null);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TeslaPlacerItem.isTeslaPlacer(player.getMainHandItem())) return;

        event.setCanceled(true);
        handleLeftClick(player, player.serverLevel());
    }

    // ---- right click: place / finalize -----------------------------------

    private static void handleRightClick(ServerPlayer player, ServerLevel level,
                                          @Nullable BlockPos clickedPos, @Nullable Direction clickedFace) {
        Optional<TeslaWaypointTargeting.Hit> hitOpt = TeslaWaypointTargeting.pick(level, player);

        if (hitOpt.isPresent()) {
            TeslaWaypointTargeting.Hit hit = hitOpt.get();
            boolean isMine = hit.source() == TeslaWaypointTargeting.Source.BUILD_SESSION
                    && player.getUUID().equals(hit.buildingPlayerId());

            if (isMine && hit.pointIndex() == 0) {
                finalizeRoute(player, level);
            }
            // any other hit (a non-start point of my own build, or any point of someone else's
            // build/route) is a no-op, per spec.
        } else if (clickedPos != null && clickedFace != null) {
            BlockPos newPoint = clickedPos.relative(clickedFace);
            TeslaBuildManager.Session session = TeslaBuildManager.getOrCreate(player);
            session.addPoint(newPoint);
            syncSession(player, session);
        }
        // right-click into empty air with nothing in reach at all: nothing to anchor a point to.

        announceActiveBuild(player);
    }

    private static void finalizeRoute(ServerPlayer player, ServerLevel level) {
        TeslaBuildManager.Session session = TeslaBuildManager.getOrNull(player);
        if (session == null || session.points().isEmpty()) return;

        UUID routeId = UUID.randomUUID();
        List<BlockPos> points = List.copyOf(session.points());

        TeslaEntity tesla = ModEntities.TESLA.get().create(level);
        UUID teslaUuid = null;
        if (tesla != null) {
            tesla.initRoute(routeId, points);
            level.addFreshEntity(tesla);
            teslaUuid = tesla.getUUID();
        }
        TeslaRouteSavedData.get(level).add(new TeslaRouteSavedData.TeslaRoute(routeId, points, teslaUuid));

        TeslaBuildManager.discard(player);
        TeslaSyncHandler.sendBuildSession(player, List.of());
        TeslaSyncHandler.broadcastRoutesFullResync(level);

        player.sendSystemMessage(Component.literal(
                "[Тесла] Маршрут завершён (" + points.size() + " точ.) — Тесла заспаунена."));
    }

    // ---- left click: remove / cancel / delete ----------------------------

    private static void handleLeftClick(ServerPlayer player, ServerLevel level) {
        Optional<TeslaWaypointTargeting.Hit> hitOpt = TeslaWaypointTargeting.pick(level, player);
        if (hitOpt.isEmpty()) {
            return; // plain block, no route point here: always a no-op
        }
        TeslaWaypointTargeting.Hit hit = hitOpt.get();
        TeslaBuildManager.Session mySession = TeslaBuildManager.getOrNull(player);
        boolean isMine = hit.source() == TeslaWaypointTargeting.Source.BUILD_SESSION
                && player.getUUID().equals(hit.buildingPlayerId());

        if (isMine) {
            List<BlockPos> points = mySession.points();
            int index = hit.pointIndex();
            if (index == 0) {
                TeslaBuildManager.discard(player);
                TeslaSyncHandler.sendBuildSession(player, List.of());
                player.sendSystemMessage(Component.literal("[Тесла] Построение маршрута отменено."));
            } else if (index >= 0 && index < points.size()) {
                points.remove(index);
                syncSession(player, mySession);
            }
        } else if (hit.source() == TeslaWaypointTargeting.Source.COMPLETED_ROUTE && mySession == null) {
            // no build of my own in progress at all: a click on any finished route deletes it whole
            deleteRoute(level, hit.routeId());
        }
        // a point belonging to someone else's in-progress build, or to a completed route while I
        // *do* have my own build going, is a no-op either way.

        announceActiveBuild(player);
    }

    private static void deleteRoute(ServerLevel level, UUID routeId) {
        TeslaRouteSavedData data = TeslaRouteSavedData.get(level);
        TeslaRouteSavedData.TeslaRoute route = data.routes().get(routeId);
        if (route == null) return;

        if (route.teslaEntityUuid() != null) {
            Entity entity = level.getEntity(route.teslaEntityUuid());
            if (entity != null) {
                entity.discard();
            }
        }
        data.remove(routeId);
        TeslaSyncHandler.broadcastRouteRemoved(level, routeId);
    }

    // ---- shared helpers -----------------------------------------------------

    private static void syncSession(ServerPlayer player, TeslaBuildManager.Session session) {
        TeslaSyncHandler.sendBuildSession(player, List.copyOf(session.points()));
    }

    private static void announceActiveBuild(ServerPlayer player) {
        TeslaBuildManager.Session session = TeslaBuildManager.getOrNull(player);
        if (session == null || session.points().isEmpty()) return;

        BlockPos start = session.startPoint();
        player.sendSystemMessage(Component.literal(String.format(
                "[Тесла] Активное построение маршрута начато в (%d, %d, %d)",
                start.getX(), start.getY(), start.getZ())));
    }
}
