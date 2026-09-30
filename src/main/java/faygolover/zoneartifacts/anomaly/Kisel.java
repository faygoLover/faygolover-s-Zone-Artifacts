package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Kisel: a puddle of bubbling, bright green, glowing liquid. Whatever falls or steps in — a
 * dropped item, a creature, an arrow — makes it seethe and hiss and glow brighter while its acid
 * eats it: items dissolve one by one, creatures are burnt (chemical damage) and their boots and
 * leggings corroded, projectiles melt away. Server logic in {@link KiselEngine}.
 */
public final class Kisel {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_chemical");
    public static final ResourceLocation IDLE_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "kisel_idle");
    public static final ResourceLocation HIT_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "kisel_hit");
    /** Seconds without contact before it calms down. */
    public static final int CALM_TICKS = 30;
    /** Something counts as "in it" up to this high over the surface. */
    public static final double CONTACT_HEIGHT = 0.6;

    private Kisel() {
    }

    /** Where touching it counts: the zone's footprint, around the surface. */
    public static AABB contactBox(AABB zone, double surfaceY) {
        return new AABB(zone.minX, surfaceY - 0.6, zone.minZ, zone.maxX, surfaceY + CONTACT_HEIGHT, zone.maxZ);
    }

    // ---- the puddle's outline (shared: the client draws it, the server burns inside it) ----------

    /** Stepping within this of the middle wakes it (it doesn't grow). */
    public static double reactRadius(double size) {
        return size * 0.5;
    }

    /** One tongue it pushes out while seething: direction, width (radians), reach (x the react
     *  radius) and how far into the seething it starts. */
    public record Tongue(double angle, double width, double reach, double start) {
    }

    public static Tongue[] tongues(long seed) {
        java.util.Random r = new java.util.Random(seed * 0x9E3779B97F4A7C15L + 17);
        Tongue[] out = new Tongue[5];
        double a0 = r.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < out.length; i++) {
            double angle = a0 + i * Math.PI * 2.0 / out.length + (r.nextDouble() - 0.5) * 0.9;
            out[i] = new Tongue(angle, 0.28 + r.nextDouble() * 0.3, 0.3 + r.nextDouble() * 0.35, i == 0 ? 0.0 : r.nextDouble() * 0.55);
        }
        return out;
    }

    /** How far a tongue has grown at this much seething, 0..1. */
    public static double tongueGrowth(Tongue t, double grow) {
        double k = Math.max(0.0, Math.min(1.0, (grow - t.start()) / Math.max(0.2, 1.0 - t.start())));
        return k * k * (3.0 - 2.0 * k);
    }

    /** The puddle's edge at angle {@code phi}: at rest a little past the react circle all round,
     *  seething it builds out in tongues ({@code grow} 0..1). */
    public static double outline(double size, long seed, Tongue[] tongues, double phi, double grow) {
        double react = reactRadius(size);
        double s0 = (seed & 0xFFFF) / 6553.6;
        // At rest: still past the react circle all round, but not a circle — a lopsided, lobed spill.
        double rest = react * (1.04 + 0.16 * (0.5 + 0.5 * Math.sin(phi * 2.0 + s0)) + 0.1 * (0.5 + 0.5 * Math.sin(phi * 3.0 - s0 * 1.7))
                + 0.06 * (0.5 + 0.5 * Math.sin(phi * 5.0 + s0 * 0.6)) + 0.03 * (0.5 + 0.5 * Math.sin(phi * 9.0 - s0)));
        double out = 0.08 * react * grow;
        for (Tongue t : tongues) {
            double d = Math.IEEEremainder(phi - t.angle(), Math.PI * 2.0);
            out += react * t.reach() * tongueGrowth(t, grow) * Math.exp(-(d / t.width()) * (d / t.width()));
        }
        return rest + out;
    }

    /** Is (x, z) within the puddle as it is now (with {@code pad} for a body's half width)? */
    public static boolean inPuddle(double cx, double cz, double size, long seed, Tongue[] tongues, double grow,
                                   double x, double z, double pad) {
        double dx = x - cx;
        double dz = z - cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        return d <= outline(size, seed, tongues, Math.atan2(dz, dx), grow) + pad;
    }

    /** The farthest the puddle ever reaches from its middle. */
    public static double maxReach(double size) {
        return reactRadius(size) * 2.2;
    }
}
