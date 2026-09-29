package faygolover.zoneartifacts.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Waypoint geometry shared by the server (click resolution) and the client (rendering and the
 * left-click raytrace), so both sides always agree on where a waypoint is and how big it is.
 * <p>
 * A waypoint is stored as the block position it occupies; the Tesla's <em>center</em> passes
 * through the center of that block. The visible marker is deliberately smaller than a block,
 * while the click box is a bit larger than the marker so small points stay easy to hit.
 */
public final class TeslaGeometry {

    /** Edge length of the drawn waypoint marker. */
    public static final double MARKER_SIZE = 0.35;

    /** Edge length of the drawn marker for a draft's start point — slightly larger so the GM can
     *  always see which point closes the route. */
    public static final double START_MARKER_SIZE = 0.45;

    /** Edge length of the invisible box used for click raytraces. */
    public static final double CLICK_BOX_SIZE = 0.5;

    /** Reach for clicking waypoints; a little over creative block reach, like Electra's zones. */
    public static final double CLICK_REACH = 6.0;

    private TeslaGeometry() {
    }

    public static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static AABB cube(Vec3 center, double size) {
        double h = size / 2.0;
        return new AABB(center.x - h, center.y - h, center.z - h, center.x + h, center.y + h, center.z + h);
    }

    public static AABB clickBox(BlockPos pos) {
        return cube(center(pos), CLICK_BOX_SIZE);
    }

    /** One waypoint of one route or draft. {@code routeId > 0} is a completed route,
     *  {@code routeId < 0} a draft still being built. */
    public record WaypointRef(int routeId, int index, BlockPos pos) {
    }

    /** A raytrace hit on a waypoint, with the squared distance from the ray origin. */
    public record WaypointHit(WaypointRef ref, double distanceSq) {
    }

    /** Nearest waypoint whose click box the segment {@code from → to} passes through. */
    public static Optional<WaypointHit> pick(Iterable<WaypointRef> waypoints, Vec3 from, Vec3 to) {
        WaypointHit best = null;
        for (WaypointRef ref : waypoints) {
            Optional<Vec3> hit = clickBox(ref.pos()).clip(from, to);
            if (hit.isEmpty()) continue;
            double distSq = from.distanceToSqr(hit.get());
            if (best == null || distSq < best.distanceSq()) {
                best = new WaypointHit(ref, distSq);
            }
        }
        return Optional.ofNullable(best);
    }

    /** Waypoint hit wins over a block hit only if it's actually closer to the eye. */
    public static boolean beatsBlock(WaypointHit hit, @Nullable Double blockHitDistanceSq) {
        return blockHitDistanceSq == null || hit.distanceSq() <= blockHitDistanceSq;
    }
}
