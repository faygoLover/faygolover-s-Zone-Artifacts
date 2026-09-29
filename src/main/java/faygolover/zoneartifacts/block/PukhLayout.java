package faygolover.zoneartifacts.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Where Burning Fluff's lace sheets hang, in block-local coordinates (shared by the renderer and
 * the parting of the strands). Under a ceiling: sheets crossing each other, so it looks full from
 * any side. On a wall: layers close to the wall, one in front of another.
 */
public final class PukhLayout {

    /** A sheet: its top-left corner, the direction across it, its width, a per-sheet seed, and on a
     *  wall how far its top leans back to the base (its fibres grow out of the base's face and
     *  fall away from it over the first {@link #LEAN_LENGTH} blocks). */
    public record Sheet(Vec3 origin, Vec3 across, double width, float seed, double lean) {
        public Sheet(Vec3 origin, Vec3 across, double width, float seed) {
            this(origin, across, width, seed, 0.0);
        }
    }

    public static final double LEAN_LENGTH = 0.45;

    private static final double TOP = faygolover.zoneartifacts.anomaly.Pukh.WALL_TOP;

    private PukhLayout() {
    }

    public static List<Sheet> sheets(Direction facing, int intensity) {
        int count = Math.max(2, Math.min(PukhBlockEntity.MAX_SHEETS, 2 + intensity));
        List<Sheet> out = new ArrayList<>();
        if (facing == Direction.DOWN || facing == Direction.UP) {
            double top = faygolover.zoneartifacts.anomaly.Pukh.CEILING_TOP;
            for (int i = 0; i < count; i++) {
                double f = (i + 0.5) / count;
                double jitter = ((i * 7919) % 11) / 11.0 * 0.12 - 0.06;
                if (i % 2 == 0) {
                    out.add(new Sheet(new Vec3(0.02, top, Math.min(0.95, Math.max(0.05, f + jitter))), new Vec3(1, 0, 0), 0.96, i * 1.7f));
                } else {
                    out.add(new Sheet(new Vec3(Math.min(0.95, Math.max(0.05, f + jitter)), top, 0.02), new Vec3(0, 0, 1), 0.96, i * 1.7f));
                }
            }
            return out;
        }
        // On a wall: the wall is on the facing's opposite side.
        Direction wall = facing.getOpposite();
        int layers = Math.max(2, Math.min(PukhBlockEntity.MAX_SHEETS, 1 + (intensity + 1) / 2));
        for (int i = 0; i < layers; i++) {
            double base = faygolover.zoneartifacts.anomaly.Pukh.WALL_BASE + 0.015;
            double depth = base + 0.16 * i / Math.max(1, layers - 1);
            Vec3 origin;
            Vec3 across;
            switch (wall) {
                case NORTH -> {
                    origin = new Vec3(0.0, TOP, depth);
                    across = new Vec3(1, 0, 0);
                }
                case SOUTH -> {
                    origin = new Vec3(0.0, TOP, 1.0 - depth);
                    across = new Vec3(1, 0, 0);
                }
                case WEST -> {
                    origin = new Vec3(depth, TOP, 0.0);
                    across = new Vec3(0, 0, 1);
                }
                default -> {
                    origin = new Vec3(1.0 - depth, TOP, 0.0);
                    across = new Vec3(0, 0, 1);
                }
            }
            out.add(new Sheet(origin, across, 1.0, i * 2.3f, depth - base));
        }
        return out;
    }
}
