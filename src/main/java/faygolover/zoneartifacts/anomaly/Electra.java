package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Electra's fixed characteristics — what used to live in {@code anomaly_types/electra.json}.
 * The adjustable parts (size, cooldown, damage, intensity) are stored per placed Electra in
 * {@link AnomalyInstance} and changed with the tuners; their defaults come from the common config.
 */
public final class Electra {

    /** Size of a freshly placed Electra, in blocks. */
    public static final double DEFAULT_SIZE = 1.0;

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_shock");

    public static final ResourceLocation IDLE_SOUND = id("electra_idle");
    public static final float IDLE_VOLUME = 0.4f;
    public static final float IDLE_PITCH = 1.0f;

    public static final ResourceLocation BLAST_LIVING_SOUND = id("electra_blast_living");
    public static final ResourceLocation BLAST_PROJECTILE_SOUND = id("electra_blast_nut");
    public static final float BLAST_VOLUME = 0.6f;

    public static final List<ResourceLocation> HIT_SOUNDS = List.of(id("electra_hit"), id("electra_hit1"));
    public static final float HIT_VOLUME = 0.8f;

    /** Ticks the strike bolts spend reaching out before they connect with the target. The
     *  electrification visual starts when they connect, not when the Electra fires. */
    public static final int STRIKE_WINDUP_TICKS = 6;

    // Ambient arc look; the number of arcs is the anomaly's intensity.
    public static final int ARC_POINTS_PER_BUNDLE = 4;
    public static final int ARC_MIN_LIFETIME_TICKS = 15;
    public static final int ARC_MAX_LIFETIME_TICKS = 25;
    public static final int ARC_COLOR = 0xB8E8FF;

    private Electra() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
