package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/**
 * The Chemical Comet's fixed characteristics. It flies routes exactly like the Tesla; on impact it
 * bursts into a heavy yellow-green cloud that creeps over the ground ({@code anomaly.ChemClouds}):
 * the burst itself hurts, the cloud keeps burning whoever is inside for {@code chemComet.cloudSeconds},
 * and plants under it die (grass turns to dirt, leaves fall). Adjustable parts on its route,
 * defaults in the {@code chemComet} section of the common config.
 */
public final class ChemComet {

    public static final ResourceLocation DAMAGE_TYPE = id("anomaly_chemical");
    public static final ResourceLocation IDLE_SOUND = id("chem_comet_idle");
    public static final float IDLE_VOLUME = 0.7f;
    public static final ResourceLocation BURST_SOUND = id("chem_comet_burst");
    public static final float BURST_VOLUME = 2.0f;

    /** Cloud radius at size 1 (+ this per extra size unit), capped. */
    public static final double CLOUD_RADIUS = 3.5;
    public static final double CLOUD_RADIUS_PER_SIZE = 1.5;
    public static final double MAX_CLOUD_RADIUS = 12.0;
    /** The cloud hugs the ground this high. */
    public static final double CLOUD_HEIGHT = 1.8;
    /** Plants die within this radius at most, however big the cloud. */
    public static final double MAX_WITHER_RADIUS = 8.0;
    /** The burst's own damage reaches this share of the cloud radius, falling to 30 % at its edge. */
    public static final double BURST_SHARE = 0.6;

    private ChemComet() {
    }

    public static double cloudRadius(double size) {
        return Math.min(MAX_CLOUD_RADIUS, CLOUD_RADIUS + CLOUD_RADIUS_PER_SIZE * Math.max(0.0, size - 1.0));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
