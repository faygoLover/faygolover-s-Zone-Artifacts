package faygolover.zoneartifacts.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * {@code config/fl_zone_arts-client.toml} — per-player visual settings, a plain file that works
 * with any launcher or config-menu mod.
 */
public final class ModClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_EFFECT_INTENSITY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MAX_EFFECT_INTENSITY = b.comment(
                        "Caps how detailed anomaly effects are drawn for you. An anomaly tuned above this",
                        "value is drawn as if it were set to this value; anomalies at or below it look",
                        "exactly as tuned. Lower it on a weak computer. 10 = no cap for standard setups.")
                .defineInRange("maxEffectIntensity", 10, 1, 50);
        SPEC = b.build();
    }

    private ModClientConfig() {
    }

    // The graphics settings live in FL Render (Options > Video Settings > FL Render): one quality preset for
    // all our mods and a switch per technique. These read them (client only — see ZaTechniques).

    /** Refraction and heat haze round the anomalies (one copy of the frame per frame while any is in view). */
    public static boolean airDistortion() {
        return faygolover.zoneartifacts.client.render.ZaTechniques.AIR_DISTORTION.on();
    }

    /** Refraction worked out per pixel (else the plain mesh refraction). */
    public static boolean pixelDistortion() {
        return faygolover.zoneartifacts.client.render.ZaTechniques.PIXEL_DISTORTION.on();
    }

    public static boolean softEdges() {
        return faygolover.zoneartifacts.client.render.ZaTechniques.SOFT_EDGES.on();
    }

    public static boolean dithering() {
        return faygolover.zoneartifacts.client.render.ZaTechniques.DITHERING.on();
    }

    public static boolean glowHalo() {
        return faygolover.zoneartifacts.client.render.ZaTechniques.GLOW_HALO.on();
    }

    public static boolean volumetricSmoke() {
        return faygolover.zoneartifacts.client.render.ZaTechniques.VOLUMETRIC_SMOKE.on();
    }

    /** Ray-marching steps through the smoke: finer at ULTRA. */
    public static int smokeSteps() {
        return faygolover.flrender.api.FlRender.quality() == faygolover.flrender.api.Quality.ULTRA ? 32 : 16;
    }

    public static boolean bubbleFilm() {
        return faygolover.zoneartifacts.client.render.ZaTechniques.BUBBLE_FILM.on();
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
