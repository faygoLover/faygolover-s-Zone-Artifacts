package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.item.TeslaRoutePlacerItem;
import faygolover.zoneartifacts.network.TeslaSync;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything a route placer (Tesla, Comet) does, server-side. A placer only sees routes of its own
 * {@link RouteKind}; every message starts with that kind's name. The rules, verbatim from the design:
 * <p>
 * <b>Right-click</b>
 * <ul>
 *     <li>block, no route being built → start a route, point 1;</li>
 *     <li>block, route being built → add the next point, report its number;</li>
 *     <li>own draft's start point → finish the route (even a one-point route) and spawn the Tesla;</li>
 *     <li>own draft's other point → nothing;</li>
 *     <li>point of another route/draft while building → nothing;</li>
 *     <li>clicking a block whose placement spot already holds a waypoint counts as clicking that waypoint.</li>
 * </ul>
 * <b>Left-click</b> (arrives via {@code TeslaWaypointClickPacket}; left-click on a plain block does nothing)
 * <ul>
 *     <li>any route's point, nothing being built → remove that whole route (with its Tesla);</li>
 *     <li>own draft's point → remove it and join its neighbours; the start point cancels the draft;</li>
 *     <li>another route's point while building → nothing.</li>
 * </ul>
 * Every message sent while a route is being built ends with that route's start coordinates, so a GM
 * who lost track of an unfinished route can always find and cancel it.
 */
public final class TeslaRouteService {

    private static final String KEY = "message.fl_zone_arts.tesla.";

    private TeslaRouteService() {
    }

    public static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    // ---- right-click -------------------------------------------------------------

    /** Right-click on a block. {@code placePos} is where a waypoint would go (neighbour on the
     *  clicked face's side, like placing a block); {@code clickLocation} is the exact hit point. */
    public static void onUseOnBlock(ServerPlayer player, RouteKind kind, BlockPos placePos, Vec3 clickLocation) {
        ServerLevel level = player.serverLevel();
        dropForeignDraft(player, level);
        if (draftOfOtherKind(player, kind)) return;

        Optional<TeslaGeometry.WaypointHit> hit = pick(player, level, kind);
        double blockDistSq = player.getEyePosition().distanceToSqr(clickLocation);
        if (hit.isPresent() && TeslaGeometry.beatsBlock(hit.get(), blockDistSq)) {
            rightClickWaypoint(player, level, kind, hit.get().ref());
            return;
        }

        Optional<TeslaGeometry.WaypointRef> occupying = waypointAt(level, kind, placePos);
        if (occupying.isPresent()) {
            rightClickWaypoint(player, level, kind, occupying.get());
            return;
        }

        TeslaDrafts.Draft draft = TeslaDrafts.of(player.getUUID());
        if (draft == null) {
            draft = TeslaDrafts.start(player.getUUID(), player.getGameProfile().getName(), level.dimension(), kind, placePos);
            send(player, kind, Component.translatable(KEY + "route_started"), draft);
        } else {
            draft.points().add(placePos.immutable());
            send(player, kind, Component.translatable(KEY + "point_added", draft.points().size(), formatPos(placePos)), draft);
        }
        TeslaSync.broadcast(level);
    }

    /** Right-click with nothing solid under the crosshair — only waypoints can be the target. */
    public static void onUseInAir(ServerPlayer player, RouteKind kind) {
        ServerLevel level = player.serverLevel();
        dropForeignDraft(player, level);
        if (draftOfOtherKind(player, kind)) return;
        pick(player, level, kind).ifPresent(hit -> rightClickWaypoint(player, level, kind, hit.ref()));
    }

    /** The GM is building a route of another kind: say so and do nothing. */
    private static boolean draftOfOtherKind(ServerPlayer player, RouteKind kind) {
        TeslaDrafts.Draft own = TeslaDrafts.of(player.getUUID());
        if (own == null || own.kind() == kind) return false;
        send(player, own.kind(), Component.translatable(KEY + "other_kind"), own);
        return true;
    }

    private static void rightClickWaypoint(ServerPlayer player, ServerLevel level, RouteKind kind, TeslaGeometry.WaypointRef ref) {
        TeslaDrafts.Draft own = TeslaDrafts.of(player.getUUID());

        if (own != null && ref.routeId() == own.id()) {
            if (ref.index() == 0) {
                complete(player, level, own);
            } else {
                send(player, kind, Component.translatable(KEY + "not_start_point", ref.index() + 1), own);
            }
            return;
        }
        if (own != null) {
            send(player, kind, Component.translatable(KEY + "other_route"), own);
            return;
        }

        // Nothing being built: just tell the GM what this point belongs to.
        if (ref.routeId() > 0) {
            TeslaRoute route = TeslaRouteSavedData.get(level).get(ref.routeId());
            if (route != null) {
                send(player, route.kind(), Component.translatable(KEY + "route_info", route.id(), route.waypoints().size(),
                        formatPos(route.waypoints().get(0))), null);
            }
        } else {
            TeslaDrafts.Draft other = TeslaDrafts.byId(ref.routeId());
            if (other != null) {
                send(player, other.kind(), Component.translatable(KEY + "draft_info", other.ownerName(), formatPos(other.start())), null);
            }
        }
    }

    private static void complete(ServerPlayer player, ServerLevel level, TeslaDrafts.Draft draft) {
        TeslaDrafts.remove(player.getUUID());
        TeslaRouteSavedData data = TeslaRouteSavedData.get(level);
        RouteKind kind = draft.kind();
        TeslaRoute route = data.create(kind, draft.points());

        TeslaEntity tesla = kind.entityType().create(level);
        if (tesla == null) {
            send(player, kind, Component.translatable(KEY + "spawn_failed", route.id()), null);
        } else {
            tesla.initOnRoute(route);
            level.addFreshEntity(tesla);
            route.setTeslaUuid(tesla.getUUID());
            route.setLastKnownTeslaPos(tesla.blockPosition());
            data.setDirty();
            send(player, kind, Component.translatable(KEY + "route_completed", route.id(), route.waypoints().size(),
                    formatPos(route.waypoints().get(0))), null);
        }
        TeslaSync.broadcast(level);
    }

    // ---- left-click --------------------------------------------------------------

    public static void onLeftClickWaypoint(ServerPlayer player, int routeId, int index, BlockPos pos) {
        if (!(player.getMainHandItem().getItem() instanceof TeslaRoutePlacerItem placer)) return;
        RouteKind kind = placer.kind();
        ServerLevel level = player.serverLevel();
        double maxDist = TeslaGeometry.CLICK_REACH + 1.0;
        if (player.getEyePosition().distanceToSqr(TeslaGeometry.center(pos)) > maxDist * maxDist) return;
        dropForeignDraft(player, level);

        TeslaDrafts.Draft own = TeslaDrafts.of(player.getUUID());

        if (routeId > 0) {
            TeslaRoute route = TeslaRouteSavedData.get(level).get(routeId);
            if (route == null || route.kind() != kind || !isPointAt(route.waypoints(), index, pos)) return;
            if (own != null) {
                send(player, kind, Component.translatable(KEY + "other_route"), own);
                return;
            }
            removeRoute(level, route);
            send(player, kind, Component.translatable(KEY + "route_removed", route.id()), null);
            TeslaSync.broadcast(level);
            return;
        }

        TeslaDrafts.Draft target = TeslaDrafts.byId(routeId);
        if (target == null || target.kind() != kind || !target.dimension().equals(level.dimension())
                || !isPointAt(target.points(), index, pos)) return;

        if (own == null) {
            // Someone else's unfinished route, and this GM isn't building one: remove it whole.
            TeslaDrafts.remove(target.owner());
            send(player, kind, Component.translatable(KEY + "draft_removed", target.ownerName()), null);
            ServerPlayer owner = player.server.getPlayerList().getPlayer(target.owner());
            if (owner != null && owner != player) {
                owner.sendSystemMessage(prefixed(kind, Component.translatable(KEY + "draft_removed_by_other", player.getGameProfile().getName())));
            }
        } else if (own.id() == target.id()) {
            if (index == 0) {
                TeslaDrafts.remove(player.getUUID());
                send(player, kind, Component.translatable(KEY + "draft_cancelled", formatPos(own.start())), null);
            } else {
                own.points().remove(index);
                send(player, kind, Component.translatable(KEY + "point_removed", index + 1, own.points().size()), own);
            }
        } else {
            send(player, kind, Component.translatable(KEY + "other_route"), own);
            return;
        }
        TeslaSync.broadcast(level);
    }

    // ---- shared helpers ------------------------------------------------------------

    /** Removes a completed route and its Tesla. A Tesla sitting in an unloaded chunk can't be
     *  reached from here; it discards itself as soon as it loads and finds its route gone. */
    public static void removeRoute(ServerLevel level, TeslaRoute route) {
        TeslaRouteSavedData.get(level).remove(route.id());
        if (route.teslaUuid() != null) {
            Entity tesla = level.getEntity(route.teslaUuid());
            if (tesla != null) tesla.discard();
        }
    }

    private static boolean isPointAt(List<BlockPos> points, int index, BlockPos pos) {
        return index >= 0 && index < points.size() && points.get(index).equals(pos);
    }

    /** Safety net: a draft left in another dimension (shouldn't survive a dimension change, but
     *  never let one block building here). */
    private static void dropForeignDraft(ServerPlayer player, ServerLevel level) {
        TeslaDrafts.Draft draft = TeslaDrafts.of(player.getUUID());
        if (draft != null && !draft.dimension().equals(level.dimension())) {
            TeslaDrafts.remove(player.getUUID());
        }
    }

    /** Waypoints of every route and draft of {@code kind} (null: every kind) in this dimension. */
    public static List<TeslaGeometry.WaypointRef> allWaypoints(ServerLevel level, @Nullable RouteKind kind) {
        List<TeslaGeometry.WaypointRef> refs = new ArrayList<>();
        for (TeslaRoute route : TeslaRouteSavedData.get(level).routes()) {
            if (kind != null && route.kind() != kind) continue;
            List<BlockPos> points = route.waypoints();
            for (int i = 0; i < points.size(); i++) {
                refs.add(new TeslaGeometry.WaypointRef(route.id(), i, points.get(i)));
            }
        }
        for (TeslaDrafts.Draft draft : TeslaDrafts.inDimension(level.dimension())) {
            if (kind != null && draft.kind() != kind) continue;
            List<BlockPos> points = draft.points();
            for (int i = 0; i < points.size(); i++) {
                refs.add(new TeslaGeometry.WaypointRef(draft.id(), i, points.get(i)));
            }
        }
        return refs;
    }

    private static Optional<TeslaGeometry.WaypointRef> waypointAt(ServerLevel level, RouteKind kind, BlockPos pos) {
        for (TeslaGeometry.WaypointRef ref : allWaypoints(level, kind)) {
            if (ref.pos().equals(pos)) return Optional.of(ref);
        }
        return Optional.empty();
    }

    private static Optional<TeslaGeometry.WaypointHit> pick(ServerPlayer player, ServerLevel level, RouteKind kind) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(TeslaGeometry.CLICK_REACH));
        return TeslaGeometry.pick(allWaypoints(level, kind), eye, end);
    }

    /** "[Тесла] message" / "[Комета] message". */
    public static MutableComponent prefixed(RouteKind kind, Component message) {
        return Component.literal("[").append(Component.translatable(kind.nameKey())).append("] ").append(message);
    }

    /** Chat message; while a route is being built, always followed by its start coordinates. */
    private static void send(ServerPlayer player, RouteKind kind, MutableComponent message, @Nullable TeslaDrafts.Draft activeDraft) {
        TeslaDrafts.Draft draft = activeDraft != null ? activeDraft : TeslaDrafts.of(player.getUUID());
        if (draft != null) {
            message.append(" ").append(Component.translatable(KEY + "current_start", formatPos(draft.start())));
        }
        player.sendSystemMessage(prefixed(kind, message));
    }
}
