package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/**
 * Fixed characteristics of the thermal anomalies — Zharka (heat) and Iney (frost). The adjustable
 * parts (size, damage interval, damage, intensity) live per placed anomaly in {@link AnomalyInstance};
 * defaults and the block-transform switches are in the common config.
 * <p>
 * Both work the same way: while anyone is inside the zone the anomaly is <i>active</i>; everyone
 * inside takes damage on one shared timer (every {@link AnomalyInstance#cooldownSeconds()}), and
 * blocks within the zone + {@code thermal.blockRadius} slowly change (see {@link ThermalEngine}).
 */
public final class Thermal {

    public static final ResourceLocation HEAT_DAMAGE_TYPE = id("anomaly_heat");
    public static final ResourceLocation COLD_DAMAGE_TYPE = id("anomaly_cold");

    /** Delay from "someone stepped in" to the first damage pulse = the visual ramp-up (0.5 s). */
    public static final int FIRST_PULSE_DELAY_TICKS = 10;

    /** How often the block transforms run while active, and how many blocks (per 1000 in the
     *  affected box) are looked at each time. Each block gets looked at every ~10 s on average. */
    public static final int BLOCK_INTERVAL_TICKS = 10;
    public static final int BLOCK_SAMPLES_PER_1000 = 50;
    public static final int BLOCK_SAMPLES_MAX = 256;

    // ---- client look & sound ---------------------------------------------------------

    /** Activity ramp (0..1): up in 0.5 s, down in 3 s. */
    public static final float ACTIVITY_UP_PER_TICK = 1.0f / 10.0f;
    public static final float ACTIVITY_DOWN_PER_TICK = 1.0f / 60.0f;

    public static final ResourceLocation ZHARKA_IDLE_SOUND = id("zharka_idle");
    public static final float ZHARKA_IDLE_VOLUME = 0.35f;
    public static final float ZHARKA_ACTIVE_VOLUME = 0.9f;

    /** Iney has no own loop yet: short vanilla-backed crackles (see sounds.json), played now and then. */
    public static final ResourceLocation INEY_IDLE_SOUND = id("iney_idle");
    public static final float INEY_IDLE_VOLUME = 0.25f;
    public static final float INEY_ACTIVE_VOLUME = 0.6f;

    private Thermal() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
