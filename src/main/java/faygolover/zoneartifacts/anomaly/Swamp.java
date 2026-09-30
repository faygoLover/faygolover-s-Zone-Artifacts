package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Trjasina (the swamp): ground that looks like any other — only unusually clean, and quiet under
 * foot — but whoever walks out onto it far enough from its edge slowly sinks into the very blocks.
 * <p>
 * It is placed <i>into</i> a block: that block's top face is the surface and stays where it is,
 * the zone grows downwards and to the sides ({@link #region}). In every column of the zone the solid
 * blocks from the surface down (as far as the zone goes, up to the first gap) are "liquefied" for
 * living things: they sink through them, but only as deep as their own sink depth lets them
 * ({@link SwampPhysics}). A pocket of air under the liquefied blocks catches whoever sinks that far.
 */
public final class Swamp {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_swamp");
    /** The last bit before the edge where one is already back on top (no step onto the ground). */
    public static final double SHORE_LIP = 0.15;
    /** How much of a step is left this deep ({@link #DEEP_AT} and deeper): the mud nearly holds one. */
    public static final double DEEP_DRAG = 0.12;
    public static final double DEEP_AT = 1.2;
    /** How fast one is pushed up when the allowed depth drops (walking towards the edge), blocks/tick. */
    public static final double RISE_PER_TICK = 0.08;
    private static final int COLUMN_REFRESH_TICKS = 40;

    private Swamp() {
    }

    /** The surface (the top face of the block it was placed into). */
    public static double surfaceY(BlockPos pos) {
        return pos.getY() + 1.0;
    }

    /** How deep the zone goes (and how far to each side from the middle, twice this). */
    public static double depth(double size) {
        return Math.max(1.0, size);
    }

    /** The zone: its top is the surface, it goes {@link #depth} down and {@code size / 2} to each side. */
    public static AABB region(BlockPos pos, double size) {
        return region(pos, size, size, size);
    }

    /** Of its own proportions: {@code sx} wide, {@code sz} long, {@code sy} deep. */
    public static AABB region(BlockPos pos, double sx, double sy, double sz) {
        double hx = Math.max(0.5, sx / 2.0);
        double hz = Math.max(0.5, sz / 2.0);
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        double top = surfaceY(pos);
        return new AABB(cx - hx, top - depth(sy), cz - hz, cx + hx, top, cz + hz);
    }

    /** Horizontal distance from (x, z) to the nearest side of the zone (negative outside it). */
    public static double shoreDistance(AABB region, double x, double z) {
        return Math.min(Math.min(x - region.minX, region.maxX - x), Math.min(z - region.minZ, region.maxZ - z));
    }

    /** Per zone: how many blocks are liquefied in each column, counted from the surface down. */
    public static final class Columns {
        final int x0;
        final int z0;
        final int w;
        final int d;
        final int[] depth;
        /** Y of the block whose top face is the surface. */
        public final int topY;
        public final AABB region;
        final double size;
        final long stamp;
        final double sx;
        final double sz;

        Columns(int x0, int z0, int w, int d, int topY, AABB region, double size, long stamp) {
            this.x0 = x0;
            this.z0 = z0;
            this.w = w;
            this.d = d;
            this.topY = topY;
            this.region = region;
            this.size = size;
            this.stamp = stamp;
            this.sx = region.getXsize();
            this.sz = region.getZsize();
            this.depth = new int[Math.max(0, w * d)];
        }

        public double surface() {
            return topY + 1.0;
        }

        /** Liquefied blocks in the column at block (x, z); 0 = not part of the swamp. */
        public int depthAt(int x, int z) {
            int i = x - x0;
            int k = z - z0;
            if (i < 0 || k < 0 || i >= w || k >= d) return 0;
            return depth[i * d + k];
        }

        public int depthAt(double x, double z) {
            return depthAt(Mth.floor(x), Mth.floor(z));
        }

        /** Is this block liquefied (for living things)? */
        public boolean liquefied(BlockPos p) {
            int n = depthAt(p.getX(), p.getZ());
            return n > 0 && p.getY() <= topY && p.getY() > topY - n;
        }

        public boolean liquefied(double x, double y, double z) {
            int n = depthAt(x, z);
            int by = Mth.floor(y);
            return n > 0 && by <= topY && by > topY - n;
        }
    }

    private static final Map<Level, Map<BlockPos, Columns>> CACHE = new WeakHashMap<>();

    /** The zone's columns, rescanned now and then (and when its size changes). */
    public static Columns columns(Level level, BlockPos pos, double size) {
        return columns(level, pos, size, size, size);
    }

    public static Columns columns(Level level, BlockPos pos, double sx, double sy, double sz) {
        Map<BlockPos, Columns> perLevel = CACHE.computeIfAbsent(level, l -> new HashMap<>());
        Columns cached = perLevel.get(pos);
        long now = level.getGameTime();
        AABB want = region(pos, sx, sy, sz);
        if (cached != null && cached.region.equals(want) && now - cached.stamp < COLUMN_REFRESH_TICKS && now >= cached.stamp) return cached;
        Columns fresh = scan(level, pos, sx, sy, sz, now);
        perLevel.put(pos.immutable(), fresh);
        if (perLevel.size() > 256) perLevel.clear();
        return fresh;
    }

    private static Columns scan(Level level, BlockPos pos, double sx, double sy, double sz, long now) {
        AABB region = region(pos, sx, sy, sz);
        double size = Math.max(sx, Math.max(sy, sz));
        // Columns whose middles are inside the zone.
        int x0 = Mth.ceil(region.minX - 0.5);
        int x1 = Mth.floor(region.maxX - 0.5);
        int z0 = Mth.ceil(region.minZ - 0.5);
        int z1 = Mth.floor(region.maxZ - 0.5);
        int maxDepth = Math.max(1, Mth.floor(depth(sy) + 1.0E-6));
        Columns c = new Columns(x0, z0, x1 - x0 + 1, z1 - z0 + 1, pos.getY(), region, size, now);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!level.isLoaded(p.set(x, pos.getY(), z))) continue;
                int n = 0;
                while (n < maxDepth) {
                    p.set(x, pos.getY() - n, z);
                    BlockState state = level.getBlockState(p);
                    if (!state.isCollisionShapeFullBlock(level, p)) break;
                    n++;
                }
                c.depth[(x - x0) * c.d + (z - z0)] = n;
            }
        }
        return c;
    }

    /** Forget the scans (a zone removed or changed a lot). */
    public static void invalidate(Level level) {
        Map<BlockPos, Columns> perLevel = CACHE.get(level);
        if (perLevel != null) perLevel.clear();
    }
}
