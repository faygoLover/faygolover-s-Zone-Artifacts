package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/**
 * The Comet's fixed characteristics. It flies routes exactly like the Tesla (same placer rules,
 * tuners, chasing of flagged players); the adjustable parts live on its {@link TeslaRoute}, their
 * defaults in the {@code comet} section of the common config.
 * <p>
 * On impact — a block, or touching a living entity — it explodes: no block damage, a push away
 * from the blast, fire damage falling off towards the edge, and fire: whatever it hit and every
 * entity in the inner radius burns, entities further out only now and then; blocks get just a few
 * scattered fires (the impact spot, then a handful at random). Both radii scale with the size. Then it is gone until the respawn delay passes.
 */
public final class Comet {

    public static final ResourceLocation DAMAGE_TYPE = id("anomaly_comet");

    public static final ResourceLocation IDLE_SOUND = id("comet_idle");
    public static final float IDLE_VOLUME = 0.8f;
    public static final float IDLE_PITCH = 1.0f;

    public static final ResourceLocation EXPLODE_SOUND = id("comet_explode");
    /** Above 1 only widens how far it is heard (16 blocks per 1.0). */
    public static final float EXPLODE_VOLUME = 2.0f;

    /** Everything within this many blocks (x size) catches fire. */
    public static final double CORE_RADIUS = 1.5;
    /** Push, damage and occasional fire reach this far (x size). */
    public static final double BLAST_RADIUS = 3.0;
    /** Block scan for fires never goes beyond this, however big the Comet. */
    public static final double MAX_BLOCK_RADIUS = 8.0;

    /** Speed added at the blast center, blocks per tick (less further out, x sqrt(size)). */
    public static final double KNOCKBACK = 0.9;
    /** Damage at the blast edge, as a share of the full damage. */
    public static final float EDGE_DAMAGE = 0.3f;
    public static final double OUTER_ENTITY_IGNITE_CHANCE = 0.35;
    /** Per empty spot: chance to become a fire candidate (then capped at 2 + 2 x size fires). */
    public static final double CORE_BLOCK_IGNITE_CHANCE = 0.12;
    public static final double OUTER_BLOCK_IGNITE_CHANCE = 0.015;

    private Comet() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
