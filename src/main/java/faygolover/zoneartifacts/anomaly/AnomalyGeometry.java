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
     * The zone's world-space box for a given size in blocks (fractional sizes allowed): centred on the
     * anchor block across (an even or fractional size simply takes part of the neighbouring blocks),
     * and standing on the anchor block's floor — it grows upwards only.
     */
    public static AABB centeredAabb(BlockPos pos, double size) {
        return centeredAabb(pos, size, size, size);
    }

    /** A box of its own proportions round the anchor block's center. */
    public static AABB centeredAabb(BlockPos pos, double sx, double sy, double sz) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        // Standing on its block's floor: a taller zone grows upwards only (0.1.35.2).
        double y0 = pos.getY();
        return new AABB(cx - sx / 2.0, y0, cz - sz / 2.0, cx + sx / 2.0, y0 + sy, cz + sz / 2.0);
    }

    public static AABB zoneAabb(AnomalyInstance instance) {
        if (AnomalyTypeIds.SWAMP.equals(instance.typeId())) return Swamp.region(instance.pos(), instance.sizeX(), instance.sizeY(), instance.sizeZ());
        return centeredAabb(instance.pos(), instance.sizeX(), instance.sizeY(), instance.sizeZ());
    }

    /** The zone as the client knows it (its own proportions where it has them). */
    public static AABB box(faygolover.zoneartifacts.network.SyncAnomaliesPacket.Entry entry) {
        return centeredAabb(entry.pos(), entry.sizeX(), entry.sizeY(), entry.sizeZ());
    }

    /** Like {@link #zoneAabb(AnomalyInstance)}, from the client's entry. */
    public static AABB zoneAabb(faygolover.zoneartifacts.network.SyncAnomaliesPacket.Entry entry) {
        if (AnomalyTypeIds.SWAMP.equals(entry.typeId())) return Swamp.region(entry.pos(), entry.sizeX(), entry.sizeY(), entry.sizeZ());
        return box(entry);
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
