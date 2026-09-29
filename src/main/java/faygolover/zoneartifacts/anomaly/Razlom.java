package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Razlom (rift): glowing cracks in the ground under it, crossing at one point, and a small
 * flame hovering over that point. When a living being (or a flying projectile) comes into its zone,
 * the flame spews a jet of fire for {@code razlom.jetSeconds} (5.5 s): the jet's end turns after
 * the target at a limited speed (a fast target can dodge it), and with no target left it keeps
 * going, drifting on and sinking to the ground, until the time is up. Then it rests for its cooldown. Server logic in {@link RazlomEngine}; the geometry here is shared
 * by the server (jet origin) and the client (cracks, flame).
 */
public final class Razlom {

    public static final ResourceLocation JET_SOUND = id("razlom_jet");
    public static final float JET_VOLUME = 1.0f;
    /** Zharka's hum, quieter. */
    public static final ResourceLocation IDLE_SOUND = id("zharka_idle");
    public static final float IDLE_VOLUME = 0.35f;
    public static final float JET_IDLE_VOLUME = 0.6f;
    /** The Cold Razlom plays the same sounds, lower. */
    public static final float COLD_PITCH = 0.72f;

    /** How high the flame hovers over the ground — right above the cracks. */
    public static final double FLAME_HEIGHT = 0.18;
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
     * always shoots upwards first and arcs over to the target like a stream of liquid — never
     * creeping along the floor. Shared by the server (what the jet hits) and the client.
     */
    public static Vec3 jetPoint(Vec3 from, Vec3 to, double t) {
        Vec3 control = jetControl(from, to);
        double u = 1.0 - t;
        return from.scale(u * u).add(control.scale(2.0 * u * t)).add(to.scale(t * t));
    }

    /** The arc's control point: a third of the way out horizontally, above the higher end by
     *  0.4 blocks plus a quarter of the horizontal distance (capped), so the stream always arches. */
    public static Vec3 jetControl(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double y = Math.max(from.y, to.y) + 0.4 + Math.min(2.0, horizontal * 0.25);
        return new Vec3(from.x + dx * 0.35, y, from.z + dz * 0.35);
    }

    /** Top of the first surface in column (x, z), scanning down from {@code fromY} to
     *  {@code toY}; null if there is none. Follows the real shape of the block at that point:
     *  a path, farmland, a slab, stairs, a snow layer or a carpet count at their own height;
     *  things you walk through (grass, flowers) and the gaps beside a fence post don't. */
    @Nullable
    public static Double groundY(BlockGetter level, double x, double z, double fromY, double toY) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        double fx = x - bx;
        double fz = z - bz;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int y = Mth.floor(fromY); y >= Mth.floor(toY); y--) {
            pos.set(bx, y, bz);
            double top = surfaceTop(level, pos, fx, fz);
            if (Double.isNaN(top)) continue;
            if (top >= 0.999) {
                above.set(bx, y + 1, bz);
                if (level.getBlockState(above).isSolidRender(level, above)) continue;
            }
            return y + top;
        }
        return null;
    }

    /** Height (0..1) of the block's top under the point (fx, fz) inside it; NaN if nothing to stand on there. */
    private static double surfaceTop(BlockGetter level, BlockPos pos, double fx, double fz) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return Double.NaN;
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        if (shape.isEmpty() && state.getBlock() instanceof SnowLayerBlock) shape = state.getShape(level, pos);
        if (shape.isEmpty()) return Double.NaN;
        double top = Double.NaN;
        for (AABB box : shape.toAabbs()) {
            if (fx < box.minX - 1.0E-4 || fx > box.maxX + 1.0E-4 || fz < box.minZ - 1.0E-4 || fz > box.maxZ + 1.0E-4) continue;
            if (Double.isNaN(top) || box.maxY > top) top = box.maxY;
        }
        return Double.isNaN(top) || top > 1.0 ? Double.NaN : top;
    }

    /** The jet's arc from {@code from} towards {@code aim}, as far as it has shot out
     *  ({@code extend}, 0..1): its points, and the block that stops it (null if none). */
    public record Arc(List<Vec3> points, @Nullable BlockHitResult hit) {
        public Vec3 end() {
            return points.get(points.size() - 1);
        }
    }

    public static Arc arc(BlockGetter level, Vec3 from, Vec3 aim, double extend) {
        List<Vec3> points = new ArrayList<>(JET_SEGMENTS + 1);
        points.add(from);
        Vec3 prev = from;
        for (int i = 1; i <= JET_SEGMENTS; i++) {
            Vec3 next = jetPoint(from, aim, extend * i / JET_SEGMENTS);
            BlockHitResult hit = clipBlocks(level, prev, next);
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getLocation());
                return new Arc(points, hit);
            }
            points.add(next);
            prev = next;
        }
        return new Arc(points, null);
    }

    /** Raycast against block collision shapes that needs no entity (works the same on both sides). */
    public static BlockHitResult clipBlocks(BlockGetter level, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, level, (getter, pos) -> {
            BlockState state = getter.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(getter, pos, CollisionContext.empty());
            return getter.clipWithInteractionOverride(from, to, pos, shape, state);
        }, getter -> {
            Vec3 d = from.subtract(to);
            return BlockHitResult.miss(to, Direction.getNearest(d.x, d.y, d.z), BlockPos.containing(to));
        });
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
