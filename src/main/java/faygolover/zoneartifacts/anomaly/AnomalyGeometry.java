package faygolover.zoneartifacts.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Shared geometry helpers, used by the tick engine, the server-side click raytrace, and the
 * client-side highlight renderer, so all three agree on exactly where an anomaly's zone is.
 */
public final class AnomalyGeometry {

    private AnomalyGeometry() {
    }

    /**
     * The zone's world-space bounding box for a given block size, centered on the anchor block
     * rather than grid-snapped. Takes a raw size rather than an {@link AnomalyShape} so it can
     * also be used client-side, where only the synced size-by-level list exists, not the full
     * (server-only) AnomalyType.
     */
    public static AABB centeredAabb(BlockPos pos, int size) {
        double half = size / 2.0;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        return new AABB(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
    }

    public static AABB zoneAabb(BlockPos pos, AnomalyShape shape, int level) {
        return centeredAabb(pos, shape.sizeForLevel(level));
    }

    public static AABB zoneAabb(AnomalyInstance instance, AnomalyType type) {
        return zoneAabb(instance.pos(), type.shape(), instance.level());
    }
}
