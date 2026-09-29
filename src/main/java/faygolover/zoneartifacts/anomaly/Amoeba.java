package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Amoeba: a puddle of translucent jelly on the ground. Someone steps into its zone — it draws
 * together into a dome ({@link #GATHER_TICKS}), rounds into a ball and lifts off the ground
 * ({@link #LIFT_TICKS}), floats higher swelling up for the rest of {@code amoeba.inflateSeconds},
 * and bursts like the Chemical Comet — only its cloud is bigger and lingers longer. Touching the
 * swelling ball burns. Afterwards a pale puddle seeps back and regains its colour over the cooldown.
 * Server logic in {@link AmoebaEngine}; the ball's shape here is shared with the client.
 */
public final class Amoeba {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_chemical");
    public static final ResourceLocation GATHER_SOUND = id("amoeba_gather");
    public static final ResourceLocation POP_SOUND = id("amoeba_pop");
    /** The burst in amoeba_pop comes this far into the sound. */
    public static final int POP_SOUND_LEAD = 26;

    public static final int GATHER_TICKS = 30;
    public static final int LIFT_TICKS = 30;
    public static final int CONTACT_INTERVAL = 10;
    /** Its cloud is this much bigger than a Chemical Comet's of the same size. */
    public static final double CLOUD_SCALE = 1.5;

    private Amoeba() {
    }

    /** Ball radius at the start of the swelling. */
    public static double domeRadius(double size) {
        return 0.3 + 0.4 * size;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** 0 = puddle, 1 = dome. */
    public static float gather(float t) {
        return smooth(t / GATHER_TICKS);
    }

    /** 0 = dome on the ground, 1 = a ball lifted off it. */
    public static float round(float t) {
        return smooth((t - GATHER_TICKS) / LIFT_TICKS);
    }

    /** 0..1 through the swelling. */
    public static float swell(float t, int inflateTicks) {
        int start = GATHER_TICKS + LIFT_TICKS;
        return Mth.clamp((t - start) / Math.max(1.0f, inflateTicks - start), 0.0f, 1.0f);
    }

    public static double radius(double size, float t, int inflateTicks) {
        return domeRadius(size) * (1.0 + 0.7 * swell(t, inflateTicks));
    }

    /** How far the bottom of the ball is off the ground. */
    public static double lift(float t, int inflateTicks) {
        return 1.0 * round(t) + 0.8 * swell(t, inflateTicks);
    }

    /** Height of the ball's middle above the ground (a dome's "middle" is at the ground). */
    public static double centerHeight(double size, float t, int inflateTicks) {
        return radius(size, t, inflateTicks) * round(t) + lift(t, inflateTicks);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
