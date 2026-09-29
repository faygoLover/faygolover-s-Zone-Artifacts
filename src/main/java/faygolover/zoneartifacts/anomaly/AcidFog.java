package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/**
 * Acid Fog: a dense greenish haze of acid vapour hovering over the ground, hard to see by day. Now
 * and then a jet of vapour and spray bursts straight up out of it (a chemical reaction going off):
 * whoever it catches is burnt and tossed up a little. Staying in the fog burns slowly too. Server
 * logic in {@link AcidFogEngine}.
 */
public final class AcidFog {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_chemical");
    public static final ResourceLocation IDLE_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "fog_idle");
    public static final ResourceLocation JET_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "fog_jet");
    /** A jet's width and height, blocks. */
    public static final double JET_RADIUS = 0.8;
    public static final double JET_HEIGHT = 3.5;
    public static final double JET_PUSH = 0.45;
    /** The haze hangs at most this high over the ground. */
    public static final double MAX_HAZE_HEIGHT = 2.2;
    public static final int IN_FOG_INTERVAL = 20;

    private AcidFog() {
    }
}
