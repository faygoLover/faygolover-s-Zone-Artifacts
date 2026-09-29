package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The Razlom (rift): glowing cracks in the ground under it, crossing at one point, and a small
 * flame hovering over that point. When a living being steps into its zone, the flame hits it with
 * a jet of fire for {@code razlom.jetSeconds} (5.5 s), following it a few blocks beyond the zone,
 * then rests for its cooldown. Server logic in {@link RazlomEngine}; the geometry here is shared
 * by the server (jet origin) and the client (cracks, flame).
 */
public final class Razlom {

    public static final ResourceLocation JET_SOUND = id("razlom_jet");
    public static final float JET_VOLUME = 1.0f;
    /** Zharka's hum, quieter. */
    public static final ResourceLocation IDLE_SOUND = id("zharka_idle");
    public static final float IDLE_VOLUME = 0.35f;
    public static final float JET_IDLE_VOLUME = 0.6f;

    /** How high the flame hovers over the ground. */
    public static final double FLAME_HEIGHT = 0.9;
    /** How far below the zone the ground (and the cracks) may be. */
    public static final int GROUND_SEARCH_BELOW = 3;

    private Razlom() {
    }

    /** Radius of the crack pattern: a bit beyond the zone. */
    public static double crackRadius(double size) {
        return Math.max(1.0, size * 0.5 + 0.75);
    }

    /** How far from the flame the jet keeps following a target. */
    public static double jetRange(double size, double extra) {
        return size * 0.5 * Math.sqrt(3.0) + extra;
    }

    /** Where the flame hovers: over the ground under the zone's center, or at the center if
     *  there is no ground close enough. */
    public static Vec3 flamePos(BlockGetter level, BlockPos pos, double size) {
        AABB zone = AnomalyGeometry.centeredAabb(pos, size);
        Vec3 c = zone.getCenter();
        Double ground = groundY(level, c.x, c.z, zone.maxY, zone.minY - GROUND_SEARCH_BELOW);
        if (ground == null) return c;
        return new Vec3(c.x, Math.min(ground + FLAME_HEIGHT, zone.maxY), c.z);
    }

    /** Top of the first solid surface in column (x, z), scanning down from {@code fromY} to
     *  {@code toY}; null if there is none. */
    @Nullable
    public static Double groundY(BlockGetter level, double x, double z, double fromY, double toY) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int y = Mth.floor(fromY); y >= Mth.floor(toY); y--) {
            pos.set(bx, y, bz);
            if (!level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) continue;
            above.set(bx, y + 1, bz);
            if (level.getBlockState(above).isSolidRender(level, above)) continue;
            return (double) (y + 1);
        }
        return null;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
