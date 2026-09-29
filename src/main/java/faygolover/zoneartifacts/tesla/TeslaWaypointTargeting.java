package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative "am I looking at a Tesla waypoint" check — the same ray/AABB-clip idea as
 * {@code AnomalyTargeting}, but against waypoint markers (both finalized routes' points and every
 * player's in-progress build, in this dimension) rather than a zone volume. Each waypoint is
 * hit-tested against a full block-sized box centered on it, so clicking one works exactly like
 * clicking a normal block even though its rendered marker is smaller.
 */
public final class TeslaWaypointTargeting {

    private static final double REACH = 6.0;

    private TeslaWaypointTargeting() {
    }

    public static Optional<Hit> pick(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getViewVector(1.0f).scale(REACH));
        ResourceLocation dimension = level.dimension().location();

        Hit closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (TeslaRouteSavedData.TeslaRoute route : TeslaRouteSavedData.get(level).routes().values()) {
            List<BlockPos> points = route.points();
            for (int i = 0; i < points.size(); i++) {
                Optional<Vec3> hit = clip(points.get(i), eye, reachEnd);
                if (hit.isEmpty()) continue;
                double distSq = eye.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = new Hit(Source.COMPLETED_ROUTE, route.routeId(), null, i, points.get(i));
                }
            }
        }

        for (Map.Entry<UUID, TeslaBuildManager.Session> entry : TeslaBuildManager.allSessionsIn(dimension).entrySet()) {
            List<BlockPos> points = entry.getValue().points();
            for (int i = 0; i < points.size(); i++) {
                Optional<Vec3> hit = clip(points.get(i), eye, reachEnd);
                if (hit.isEmpty()) continue;
                double distSq = eye.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = new Hit(Source.BUILD_SESSION, null, entry.getKey(), i, points.get(i));
                }
            }
        }

        return Optional.ofNullable(closest);
    }

    private static Optional<Vec3> clip(BlockPos pos, Vec3 eye, Vec3 reachEnd) {
        AABB aabb = AnomalyGeometry.centeredAabb(pos, 1);
        return aabb.clip(eye, reachEnd);
    }

    public enum Source {
        COMPLETED_ROUTE, BUILD_SESSION
    }

    public record Hit(Source source, @Nullable UUID routeId, @Nullable UUID buildingPlayerId, int pointIndex, BlockPos pos) {
    }
}
