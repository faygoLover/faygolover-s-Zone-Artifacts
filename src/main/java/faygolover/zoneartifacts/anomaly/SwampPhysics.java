package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How a living thing moves in the swamp ({@link Swamp}), shared by the server (mobs) and the client
 * (its own player — players move themselves). Without touching vanilla's collision code: while in
 * the swamp the entity is set to pass through blocks ({@code noPhysics}), and after it has moved we
 * redo the collision ourselves — every block counts as usual except the liquefied ones, which only
 * hold it up at its own sink depth (a floor that slowly goes down).
 * <ol>
 *     <li>{@link #begin} before it moves (at the start of its tick): pass-through on, the mud's drag.</li>
 *     <li>{@link #finish} after it moved: our collision, standing on the sinking floor, deeper.</li>
 * </ol>
 * The sink depth never exceeds {@link Swamp#SHORE_SLOPE} per block of distance from the edge, so
 * walking out raises one step by step. Below the liquefied blocks: solid ground holds as usual; an
 * air pocket lets it fall (it is no longer "in the swamp" then).
 */
public final class SwampPhysics {

    /** One swamp as seen from either side. */
    public record Zone(BlockPos pos, double size, double speed, float damage, double cooldownSeconds) {
    }

    public static final class State {
        public Zone zone;
        /** How deep its floor is under the surface, blocks. */
        public double depth;
        Vec3 pre = Vec3.ZERO;
        boolean supported;
        public int submergedTicks;
        public int soundTicks;
    }

    private SwampPhysics() {
    }

    /** Can this one sink at all? */
    public static boolean eligible(LivingEntity e) {
        if (!e.isAlive() || e.isSpectator() || e.isPassenger()) return false;
        if (e instanceof Player p && (p.getAbilities().flying || p.isCreative())) return false;
        return !e.isFallFlying();
    }

    /** The swamp it is in (standing on or sunk into), if any. */
    @Nullable
    public static Zone find(Level level, LivingEntity e, List<Zone> zones, @Nullable State state) {
        double x = e.getX();
        double z = e.getZ();
        double feet = e.getY();
        for (Zone zone : zones) {
            if (!zone.pos().closerThan(e.blockPosition(), zone.size() + 8.0)) continue;
            Swamp.Columns cols = Swamp.columns(level, zone.pos(), zone.size());
            int n = cols.depthAt(x, z);
            if (n <= 0) continue;
            double surface = cols.surface();
            if (feet > surface + 0.6) continue;
            if (feet < surface - n - 0.3) continue; // fell out of the bottom (a pocket)
            return zone;
        }
        return null;
    }

    /** Before it moves. Returns its state, or null if it isn't in a swamp (and releases it if it was). */
    @Nullable
    public static State begin(LivingEntity e, List<Zone> zones, Map<LivingEntity, State> states) {
        State s = states.get(e);
        Zone zone = eligible(e) ? find(e.level(), e, zones, s) : null;
        if (zone == null) {
            if (s != null) {
                release(e);
                states.remove(e);
            }
            return null;
        }
        if (s == null) {
            s = new State();
            states.put(e, s);
        }
        s.zone = zone;
        s.pre = e.position();
        e.noPhysics = true;
        if (s.depth > 0.05) {
            // Mud: every step is heavy.
            double f = 1.0 - 0.6 * Math.min(1.0, s.depth / 1.4);
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * f, v.y, v.z * f);
        }
        return s;
    }

    public static void release(LivingEntity e) {
        e.noPhysics = e.isSpectator();
    }

    /** After it moved: our own collision, then deeper into the mud. */
    public static void finish(LivingEntity e, State s) {
        Level level = e.level();
        Swamp.Columns cols = Swamp.columns(level, s.zone.pos(), s.zone.size());
        double surface = cols.surface();
        Vec3 pre = s.pre;
        Vec3 move = e.position().subtract(pre);
        if (move.lengthSqr() > 16.0) {
            // Teleported: nothing to redo.
            s.pre = e.position();
            return;
        }
        AABB box0 = e.getBoundingBox().move(pre.subtract(e.position()));
        double floorY = surface - s.depth;
        boolean onFloorLevel = pre.y >= floorY - 1.0E-3;

        List<VoxelShape> shapes = shapes(level, e, cols, box0.expandTowards(move).inflate(0.5), floorY, onFloorLevel);
        Vec3 done = collide(box0, move, shapes);
        boolean clippedX = Math.abs(done.x - move.x) > 1.0E-7;
        boolean clippedZ = Math.abs(done.z - move.z) > 1.0E-7;
        if ((clippedX || clippedZ) && (s.supported || move.y < 0.0)) {
            // Step up (a slab, a stair) like vanilla does.
            Vec3 stepped = step(box0, move, shapes, e.maxUpStep());
            if (stepped != null && horizontalSq(stepped) > horizontalSq(done) + 1.0E-7) {
                done = stepped;
                clippedX = Math.abs(done.x - move.x) > 1.0E-7;
                clippedZ = Math.abs(done.z - move.z) > 1.0E-7;
            }
        }
        boolean clippedY = Math.abs(done.y - move.y) > 1.0E-7;
        boolean down = move.y < 0.0 && clippedY;
        Vec3 end = pre.add(done);

        // Deep in, one can't wade towards the edge faster than the mud lets one rise: otherwise one
        // would come out of it still under the ground next to it.
        double half = e.getBbWidth() / 2.0;
        double sunk = surface - end.y;
        if (sunk > allowed(cols, end.x, end.z, half) + Swamp.RISE_PER_TICK + 1.0E-3
                && Swamp.shoreDistance(cols.region, end.x, end.z) < Swamp.shoreDistance(cols.region, pre.x, pre.z)) {
            end = new Vec3(pre.x, end.y, pre.z);
            clippedX = clippedX || Math.abs(move.x) > 1.0E-7;
            clippedZ = clippedZ || Math.abs(move.z) > 1.0E-7;
        }

        // Walking towards the edge the allowed depth drops: pushed up gently.
        int column = cols.depthAt(end.x, end.z);
        if (end.y < floorY - 1.0E-4 && column > 0 && end.y >= surface - column - 0.01) {
            end = end.add(0.0, Math.min(floorY - end.y, Swamp.RISE_PER_TICK), 0.0);
            down = true;
        }
        e.setPos(end.x, end.y, end.z);
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(clippedX ? 0.0 : v.x, down || clippedY ? 0.0 : v.y, clippedZ ? 0.0 : v.z);
        e.setOnGround(down);
        e.verticalCollision = clippedY || down;
        e.verticalCollisionBelow = down;
        e.horizontalCollision = clippedX || clippedZ;
        if (down) e.resetFallDistance();
        s.supported = down;

        // Deeper — or held up near the edge.
        double allowed = allowed(cols, e.getX(), e.getZ(), half);
        if (down && allowed > 0.0) {
            s.depth += ModCommonConfig.SWAMP_SINK_SPEED.get() * Math.max(0.0, s.zone.speed()) / 20.0;
        }
        s.depth = Mth.clamp(Math.min(s.depth, allowed), 0.0, Math.max(0, cols.depthAt(e.getX(), e.getZ())));
        s.pre = e.position();
    }

    /** How deep one may sink here: near the edge (measured from one's own side) a slope out. */
    public static double allowed(Swamp.Columns cols, double x, double z, double halfWidth) {
        double shore = Swamp.shoreDistance(cols.region, x, z) - halfWidth;
        return shore < Swamp.SHORE ? Math.max(0.0, shore) * Swamp.SHORE_SLOPE : Double.MAX_VALUE;
    }

    /** Is its head under the surface, in the mud? */
    public static boolean submerged(LivingEntity e, Swamp.Columns cols) {
        Vec3 eye = e.getEyePosition();
        return eye.y < cols.surface() - 0.02 && cols.liquefied(eye.x, eye.y, eye.z);
    }

    private static double horizontalSq(Vec3 v) {
        return v.x * v.x + v.z * v.z;
    }

    /** Everything solid around except the liquefied blocks — plus, under it, the sinking floor. */
    private static List<VoxelShape> shapes(Level level, LivingEntity e, Swamp.Columns cols, AABB area, double floorY,
                                           boolean withFloor) {
        List<VoxelShape> out = new ArrayList<>();
        CollisionContext ctx = CollisionContext.of(e);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = Mth.floor(area.minX);
        int x1 = Mth.floor(area.maxX);
        int y0 = Mth.floor(area.minY);
        int y1 = Mth.floor(area.maxY);
        int z0 = Mth.floor(area.minZ);
        int z1 = Mth.floor(area.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int n = cols.depthAt(x, z);
                for (int y = y0; y <= y1; y++) {
                    p.set(x, y, z);
                    if (n > 0 && y <= cols.topY && y > cols.topY - n) continue; // liquefied
                    if (!level.isLoaded(p)) continue;
                    BlockState state = level.getBlockState(p);
                    VoxelShape shape = state.getCollisionShape(level, p, ctx);
                    if (!shape.isEmpty()) out.add(shape.move(x, y, z));
                }
                // The floor it stands on, where there still is mud under it.
                if (withFloor && n > 0 && floorY > cols.surface() - n + 1.0E-6 && floorY <= cols.surface() + 1.0E-6) {
                    out.add(Shapes.create(new AABB(x, floorY - 0.5, z, x + 1, floorY, z + 1)));
                }
            }
        }
        return out;
    }

    private static Vec3 collide(AABB box, Vec3 move, List<VoxelShape> shapes) {
        double dy = move.y;
        if (dy != 0.0) {
            dy = Shapes.collide(Direction.Axis.Y, box, shapes, dy);
            box = box.move(0.0, dy, 0.0);
        }
        double dx = move.x;
        double dz = move.z;
        boolean xFirst = Math.abs(dx) >= Math.abs(dz);
        if (xFirst && dx != 0.0) {
            dx = Shapes.collide(Direction.Axis.X, box, shapes, dx);
            box = box.move(dx, 0.0, 0.0);
        }
        if (dz != 0.0) {
            dz = Shapes.collide(Direction.Axis.Z, box, shapes, dz);
            box = box.move(0.0, 0.0, dz);
        }
        if (!xFirst && dx != 0.0) {
            dx = Shapes.collide(Direction.Axis.X, box, shapes, dx);
        }
        return new Vec3(dx, dy, dz);
    }

    @Nullable
    private static Vec3 step(AABB box, Vec3 move, List<VoxelShape> shapes, float stepHeight) {
        if (stepHeight <= 0.0f) return null;
        double up = Shapes.collide(Direction.Axis.Y, box, shapes, stepHeight);
        if (up <= 1.0E-4) return null;
        AABB raised = box.move(0.0, up, 0.0);
        Vec3 flat = collide(raised, new Vec3(move.x, 0.0, move.z), shapes);
        AABB moved = raised.move(flat.x, 0.0, flat.z);
        double down = Shapes.collide(Direction.Axis.Y, moved, shapes, -up + Math.min(0.0, move.y));
        return new Vec3(flat.x, up + down, flat.z);
    }
}
