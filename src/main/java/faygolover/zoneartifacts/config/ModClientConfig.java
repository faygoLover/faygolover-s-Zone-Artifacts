package faygolover.zoneartifacts.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * {@code config/fl_zone_arts-client.toml} — per-player visual settings, a plain file that works
 * with any launcher or config-menu mod.
 */
public final class ModClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_EFFECT_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue AIR_DISTORTION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MAX_EFFECT_INTENSITY = b.comment(
                        "Caps how detailed anomaly effects are drawn for you. An anomaly tuned above this",
                        "value is drawn as if it were set to this value; anomalies at or below it look",
                        "exactly as tuned. Lower it on a weak computer. 10 = no cap for standard setups.")
                .defineInRange("maxEffectIntensity", 10, 1, 50);
        AIR_DISTORTION = b.comment(
                        "Refraction and heat haze around anomalies (Voronka's lens, Zharka's shimmer, ...).",
                        "Costs one copy of the frame per frame while any is in view. Turn off if it looks",
                        "wrong with a shader pack or other rendering mods.")
                .define("airDistortion", true);
        SPEC = b.build();
    }

    private ModClientConfig() {
    }

    public static boolean airDistortion() {
        try {
            return AIR_DISTORTION.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /** The intensity actually drawn: the anomaly's own setting, capped by this player's limit. */
    public static int effective(int anomalyIntensity) {
        int cap;
        try {
            cap = MAX_EFFECT_INTENSITY.get();
        } catch (IllegalStateException notLoadedYet) {
            cap = 50;
        }
        return Math.max(1, Math.min(anomalyIntensity, cap));
    }
}
