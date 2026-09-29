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

    /** How high the flame hovers over the ground — right above the cracks. */
    public static final double FLAME_HEIGHT = 0.3;
    /** Points along the jet's curve (for drawing and for checking what blocks it). */
    public static final int JET_SEGMENTS = 12;
    /** The jet doesn't appear at once: it shoots out along its arc over this many ticks, and the
     *  first hit lands when it arrives. */
    public static final int JET_GROW_TICKS = 5;
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

    /**
     * Point {@code t} (0..1) of the jet's arc from the flame to the target: a quadratic curve that
     * shoots up first and then sideways, reaching the target from below and the side (never
     * dropping onto it from above). Shared by the server (what the jet hits) and the client.
     */
    public static Vec3 jetPoint(Vec3 from, Vec3 to, double t) {
        Vec3 control = jetControl(from, to);
        double u = 1.0 - t;
        return from.scale(u * u).add(control.scale(2.0 * u * t)).add(to.scale(t * t));
    }

    /** The arc's control point: a third of the way out horizontally, nearly at the target's
     *  height, a little above the flame at least. */
    public static Vec3 jetControl(Vec3 from, Vec3 to) {
        double rise = Math.max(0.0, to.y - from.y);
        double y = from.y + rise * 0.95 + 0.25;
        return new Vec3(from.x + (to.x - from.x) * 0.3, y, from.z + (to.z - from.z) * 0.3);
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
