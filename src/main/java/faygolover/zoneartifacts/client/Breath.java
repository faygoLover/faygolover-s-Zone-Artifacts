package faygolover.zoneartifacts.client;

/**
 * Slow, smooth, never-repeating breathing for fire and glow — like a gas flame or a ball of boiling
 * magma: it swells and eases, but doesn't flicker. A sum of a few slow waves whose periods don't
 * line up (about 3.5 to 10 seconds), so the rhythm never settles into a beat.
 */
public final class Breath {

    private Breath() {
    }

    /** -1..1 at {@code ticks} (game time plus partial tick); {@code seed} sets this one apart from others. */
    public static float of(float ticks, float seed) {
        double t = ticks;
        double v = 0.55 * Math.sin(t * 0.09 + seed)
                + 0.3 * Math.sin(t * 0.053 + seed * 1.7)
                + 0.15 * Math.sin(t * 0.031 + seed * 2.3);
        return (float) v;
    }
}
