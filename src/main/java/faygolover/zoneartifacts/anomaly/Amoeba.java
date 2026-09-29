package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The Amoeba: a puddle of translucent jelly on the ground. Someone steps into its zone — it
 * gathers into a quivering dome ({@link #GATHER_TICKS}) and for {@code amoeba.attackSeconds} lashes
 * out with pseudopods: most aimed at where a creature stood when the lash began (you can dodge),
 * the rest whipping about at random. A lash that catches someone burns with chemical damage and
 * eats into their armour. Then it flows back into a paler puddle and rests; after the cooldown
 * its colour comes back. Server logic in {@link AmoebaEngine}, the look is the client's.
 */
public final class Amoeba {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_chemical");
    public static final ResourceLocation GATHER_SOUND = id("amoeba_gather");
    public static final ResourceLocation LASH_SOUND = id("amoeba_lash");
    public static final ResourceLocation SETTLE_SOUND = id("amoeba_settle");

    public static final int GATHER_TICKS = 10;
    public static final int SETTLE_TICKS = 20;
    /** A lash shoots out over this many ticks (and hits then), holds, pulls back. */
    public static final int LASH_EXTEND = 5;
    public static final int LASH_HOLD = 2;
    public static final int LASH_RETRACT = 6;
    public static final int LASH_TICKS = LASH_EXTEND + LASH_HOLD + LASH_RETRACT;
    /** Share of lashes aimed at someone (when anyone is in reach). */
    public static final double AIMED_SHARE = 0.65;
    /** How thick a lash is for hitting, blocks. */
    public static final double LASH_HIT_RADIUS = 0.45;

    private Amoeba() {
    }

    /** Dome radius at full gather. */
    public static double domeRadius(double size) {
        return 0.3 + 0.4 * size;
    }

    /** How far the pseudopods reach from the dome. */
    public static double reach(double size) {
        return 1.5 + 1.5 * size;
    }

    /** Where a lash to {@code tip} starts on the dome of radius {@code dome} over {@code base}. */
    public static Vec3 lashOrigin(Vec3 base, double dome, Vec3 tip) {
        Vec3 flat = new Vec3(tip.x - base.x, 0.0, tip.z - base.z);
        Vec3 dir = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
        return base.add(dir.scale(dome * 0.45)).add(0.0, dome * 0.8, 0.0);
    }

    /** Point {@code s} (0..1) of a lash: a whip arching over from the dome to the tip. */
    public static Vec3 lashPoint(Vec3 origin, Vec3 tip, double s) {
        double len = origin.distanceTo(tip);
        Vec3 control = origin.lerp(tip, 0.4).add(0.0, 0.35 + 0.25 * len, 0.0);
        double u = 1.0 - s;
        return origin.scale(u * u).add(control.scale(2.0 * u * s)).add(tip.scale(s * s));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
