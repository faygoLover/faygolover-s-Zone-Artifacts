package faygolover.zoneartifacts.anomaly;

import java.util.List;

/**
 * Geometry of an anomaly's zone. Stage 1 only implements {@link ShapeType#CUBE}: a volume
 * centered on the anomaly's anchor block, sized (in blocks) per level. The volume is centered
 * on the block's center rather than snapped to the block grid, so e.g. a size-2 cube spans parts
 * of the four neighboring blocks symmetrically instead of jumping a whole block to one side.
 */
public record AnomalyShape(ShapeType type, List<Integer> sizesByLevel) {

    public enum ShapeType {
        CUBE
    }

    public int maxLevel() {
        return sizesByLevel.size();
    }

    /** Clamps to the valid level range so a bad level value never throws. */
    public int sizeForLevel(int level) {
        int index = Math.max(1, Math.min(level, sizesByLevel.size())) - 1;
        return sizesByLevel.get(index);
    }
}
