package faygolover.zoneartifacts.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared geometry helpers, used by the tick engine, the server-side click raytrace, and the
 * client-side renderers, so all of them agree on exactly where an anomaly's zone is.
 */
public final class AnomalyGeometry {

    private AnomalyGeometry() {
    }

    /**
     * The zone's world-space box for a given size in blocks (fractional sizes allowed), centered on
     * the anchor block's center rather than snapped to the block grid — an even or fractional size
     * simply takes part of the neighbouring blocks.
     */
    public static AABB centeredAabb(BlockPos pos, double size) {
        double half = size / 2.0;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        return new AABB(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
    }

    public static AABB zoneAabb(AnomalyInstance instance) {
        return zoneAabb(instance.typeId(), instance.pos(), instance.size());
    }

    /** The zone of an anomaly of this type: a cube round its block, except the swamp, which hangs
     *  from the top face of its block downwards ({@link Swamp#region}). */
    public static AABB zoneAabb(net.minecraft.resources.ResourceLocation typeId, BlockPos pos, double size) {
        if (AnomalyTypeIds.SWAMP.equals(typeId)) return Swamp.region(pos, size);
        return centeredAabb(pos, size);
    }

    /** True if the center of block {@code pos} lies inside the zone. */
    public static boolean containsBlockCenter(AABB zone, BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        return zone.contains(c.x, c.y, c.z);
    }
}
