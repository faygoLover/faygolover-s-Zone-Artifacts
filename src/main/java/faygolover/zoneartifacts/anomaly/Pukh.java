package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Burning Fluff (Жгучий пух): a plant-like anomaly, a real block. A fleshy base grows on the
 * underside of a block (then the fluff hangs from it like a beard) or on a wall (then it hangs
 * down the wall like a creeper); lacy, cobweb-like strands hang from it, sway, part around
 * whatever pushes through and shed dark flakes. Standing in the strands burns (chemical damage,
 * every {@link #CONTACT_INTERVAL} ticks). Anything that comes near fast — running, jumping,
 * falling, a thrown thing — gets a puff of burning spores shot at it. Geometry shared by both sides.
 */
public final class Pukh {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_chemical");
    public static final ResourceLocation PUFF_SOUND = id("pukh_puff");

    public static final int CONTACT_INTERVAL = 20;
    /** Faster than this (blocks per tick) counts as "fast": a sprint (0.28) is, a walk (0.22) isn't. */
    public static final double FAST = 0.25;
    /** Spore puffs slow down like this every tick (their start speed is range x (1 - drag)). */
    public static final double SPORE_DRAG = 0.9;
    public static final int SPORE_LIFE = 45;
    /** Thickness of the curtain on a wall. */
    public static final double WALL_DEPTH = 0.3;
    /** Where the strands start under a ceiling base (just inside its underside). */
    public static final double CEILING_TOP = 0.825;
    /** On a wall: just under the ledge at the top of the base. */
    public static final double WALL_TOP = 0.84;

    public static final double MIN_LENGTH = 0.5;
    /** The lace texture is this long: longer strands would only repeat it. */
    public static final double MAX_LENGTH = 4.0;
    public static final double MAX_RANGE = 16.0;

    private Pukh() {
    }

    /** Top of the strands. {@code facing}: DOWN = under a ceiling, horizontal = away from its wall. */
    public static double strandTop(BlockPos pos, Direction facing) {
        return facing == Direction.DOWN || facing == Direction.UP ? pos.getY() + CEILING_TOP : pos.getY() + WALL_TOP;
    }

    /** How far the strands really hang: {@code length}, cut short by the first solid block below. */
    public static double effectiveLength(BlockGetter level, BlockPos pos, Direction facing, double length) {
        double top = strandTop(pos, facing);
        int cells = (int) Math.ceil(length + 1.0);
        for (int i = 1; i <= cells; i++) {
            BlockPos below = pos.below(i);
            if (level.getBlockState(below).isCollisionShapeFullBlock(level, below)) {
                return Math.max(0.2, Math.min(length, top - (below.getY() + 1.0)));
            }
        }
        return length;
    }

    /** The space the strands hang in (touching it burns). */
    public static AABB hangingBox(BlockPos pos, Direction facing, double length) {
        double top = strandTop(pos, facing);
        double x0 = pos.getX();
        double z0 = pos.getZ();
        if (facing == Direction.DOWN || facing == Direction.UP) {
            return new AABB(x0, top - length, z0, x0 + 1.0, top, z0 + 1.0);
        }
        // Against the wall on the opposite side of the facing.
        Direction wall = facing.getOpposite();
        double minX = x0;
        double maxX = x0 + 1.0;
        double minZ = z0;
        double maxZ = z0 + 1.0;
        switch (wall) {
            case NORTH -> maxZ = z0 + WALL_DEPTH;
            case SOUTH -> minZ = z0 + 1.0 - WALL_DEPTH;
            case WEST -> maxX = x0 + WALL_DEPTH;
            case EAST -> minX = x0 + 1.0 - WALL_DEPTH;
            default -> {
            }
        }
        return new AABB(minX, top - length, minZ, maxX, top, maxZ);
    }

    /** Where puffs are shot from: the middle of the strands. */
    public static Vec3 puffOrigin(BlockPos pos, Direction facing, double length) {
        AABB box = hangingBox(pos, facing, length);
        return new Vec3((box.minX + box.maxX) * 0.5, box.maxY - Math.min(length, 1.5) * 0.5, (box.minZ + box.maxZ) * 0.5);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
