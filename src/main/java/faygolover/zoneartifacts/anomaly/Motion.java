package faygolover.zoneartifacts.anomaly;

import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How far something moved since the last tick, as the anomalies see it. Not from {@code xo}: the
 * anomalies run at the end of the level's tick, when every entity's old position (players' too —
 * their moves arrive by packet before it) has already been set to where it is now, so a walking
 * player looked as if standing still. Here each one's position is remembered tick by tick.
 */
public final class Motion {

    /** Per entity: {tick, x, y, z (now), px, py, pz (the tick before)}. */
    private static final Map<Entity, double[]> SEEN = new WeakHashMap<>();

    private Motion() {
    }

    /** Distance moved since the previous tick (0 the first time it is looked at). */
    public static double moved(Entity e, long now) {
        double[] m = step(e, now);
        return Math.sqrt(sq(m[1] - m[4]) + sq(m[2] - m[5]) + sq(m[3] - m[6]));
    }

    /** The same, across only. */
    public static double movedAcross(Entity e, long now) {
        double[] m = step(e, now);
        return Math.sqrt(sq(m[1] - m[4]) + sq(m[3] - m[6]));
    }

    private static double[] step(Entity e, long now) {
        double[] m = SEEN.get(e);
        if (m == null) {
            m = new double[]{now, e.getX(), e.getY(), e.getZ(), e.getX(), e.getY(), e.getZ()};
            SEEN.put(e, m);
            return m;
        }
        if ((long) m[0] != now) {
            boolean last = (long) m[0] == now - 1;
            m[4] = last ? m[1] : e.getX();
            m[5] = last ? m[2] : e.getY();
            m[6] = last ? m[3] : e.getZ();
            m[0] = now;
            m[1] = e.getX();
            m[2] = e.getY();
            m[3] = e.getZ();
        }
        return m;
    }

    private static double sq(double v) {
        return v * v;
    }
}
