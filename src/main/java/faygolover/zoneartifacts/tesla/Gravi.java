package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/**
 * Gravi's fixed characteristics. It flies routes like the Tesla but is invisible, has a tiny
 * hitbox ({@link #HITBOX}), passes through blocks and creatures and never pops. All the way it sets
 * off small gravitational pops ({@link GraviEntity}): on the surfaces around it within its size,
 * and now and then right by itself, in the air too. Chasing, it keeps within {@code gravi.leash}
 * blocks of its route and, once it reaches the target, hangs inside it.
 */
public final class Gravi {

    public static final float HITBOX = 0.25f;
    public static final ResourceLocation POP_SOUND = id("gravy_hit");
    public static final ResourceLocation DAMAGE_TYPE = id("anomaly_gravity");
    /** A pop sucks in for this long, then bursts. */
    public static final int WINDUP_TICKS = 8;
    /** Whoever is this close to a pop gets hurt and shoved. */
    public static final double POP_RADIUS = 1.2;
    public static final double POP_PUSH = 0.35;
    /** Surface pops per second are capped, however big it is. */
    public static final double MAX_POPS_PER_SECOND = 10.0;
    /** How often a footprint lands on a wall or the ceiling beside the step instead of the floor. */
    public static final float OFF_FLOOR_CHANCE = 0.2f;

    private Gravi() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
