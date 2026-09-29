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

    /** Delay from "someone stepped in" to the first damage pulse = the visual ramp-up (0.2 s). */
    public static final int FIRST_PULSE_DELAY_TICKS = 4;

    /** How often the block transforms run while active, and how many blocks (per 1000 in the
     *  affected box) are looked at each time. Each block gets looked at every ~10 s on average. */
    public static final int BLOCK_INTERVAL_TICKS = 10;
    public static final int BLOCK_SAMPLES_PER_1000 = 50;
    public static final int BLOCK_SAMPLES_MAX = 256;

    /** Iney's snow: a sampled empty spot in the zone gets a layer 1 time in N, a layer grows 1 in N
     *  (so a fresh layer takes ~2 min on average, the next ones ~3 min each). */
    public static final int SNOW_SETTLE_ONE_IN = 12;
    public static final int SNOW_GROW_ONE_IN = 18;

    // ---- client look & sound ---------------------------------------------------------

    /** Activity ramp (0..1): up in 0.2 s, down in 3 s. */
    public static final float ACTIVITY_UP_PER_TICK = 1.0f / 4.0f;
    public static final float ACTIVITY_DOWN_PER_TICK = 1.0f / 60.0f;

    public static final ResourceLocation ZHARKA_IDLE_SOUND = id("zharka_idle");
    public static final float ZHARKA_IDLE_VOLUME = 0.7f;
    public static final float ZHARKA_ACTIVE_VOLUME = 1.0f;

    /** Iney's crackles (vanilla freezing sounds, see sounds.json) — played now and then, per-tick
     *  chance idle ~ every 3 s, active ~ every 0.6 s — and the sound when it activates (ice_enter). */
    public static final ResourceLocation INEY_IDLE_SOUND = id("iney_idle");
    public static final ResourceLocation INEY_ENTER_SOUND = id("iney_enter");
    public static final float INEY_IDLE_CHANCE = 1.0f / 60.0f;
    public static final float INEY_ACTIVE_CHANCE = 1.0f / 12.0f;
    public static final float INEY_IDLE_VOLUME = 0.5f;
    public static final float INEY_ACTIVE_VOLUME = 0.9f;
    public static final float INEY_ENTER_VOLUME = 1.0f;

    private Thermal() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
