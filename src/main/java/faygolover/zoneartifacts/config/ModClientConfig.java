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
    public static final ForgeConfigSpec.EnumValue<Quality> QUALITY;
    public static final ForgeConfigSpec.EnumValue<Toggle> PIXEL_DISTORTION;
    public static final ForgeConfigSpec.EnumValue<Toggle> ANOMALY_LIGHT;
    public static final ForgeConfigSpec.EnumValue<Toggle> SOFT_EDGES;
    public static final ForgeConfigSpec.EnumValue<Toggle> DITHERING;
    public static final ForgeConfigSpec.EnumValue<Toggle> GLOW_HALO;
    public static final ForgeConfigSpec.EnumValue<Toggle> VOLUMETRIC_SMOKE;
    public static final ForgeConfigSpec.EnumValue<Toggle> BUBBLE_FILM;

    /** How much the anomalies' graphics may cost. LOW draws them the plain, cheap way (for weak computers). */
    public enum Quality {
        LOW, MEDIUM, HIGH, ULTRA
    }

    /** One graphics technique: as the quality says, or always on / off. */
    public enum Toggle {
        AUTO, ON, OFF
    }

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
        b.push("graphics");
        QUALITY = b.comment(
                        "How much the anomalies' graphics may cost your computer:",
                        "  LOW    - the plain, cheap way (a weak computer, an old video card);",
                        "  MEDIUM - refraction worked out for every pixel;",
                        "  HIGH   - the default;",
                        "  ULTRA  - everything at its best.",
                        "Each technique below can also be switched on or off by itself (AUTO = as the quality says).")
                .defineEnum("quality", Quality.HIGH);
        PIXEL_DISTORTION = b.comment(
                        "Refraction worked out for every pixel: the air boils, colours part (MEDIUM and up).",
                        "OFF = the plain refraction of LOW. Needs airDistortion on.")
                .defineEnum("pixelDistortion", Toggle.AUTO);
        ANOMALY_LIGHT = b.comment(
                        "Burning and glowing anomalies light up the ground and walls around them, in their own colour",
                        "(the picture only: light levels and mob spawning don't change). HIGH and up.")
                .defineEnum("anomalyLight", Toggle.AUTO);
        SOFT_EDGES = b.comment(
                        "Gas, puddles, glows and smoke melt into the ground and walls instead of ending in a hard line",
                        "where they meet them (MEDIUM and up).")
                .defineEnum("softEdges", Toggle.AUTO);
        DITHERING = b.comment(
                        "A trace of noise in soft gradients (glows, smoke) so they don't fall into visible bands (MEDIUM and up).")
                .defineEnum("dithering", Toggle.AUTO);
        GLOW_HALO = b.comment(
                        "A soft glow of the air round the fireballs (Comets, the Tesla); the balls themselves stay as they are",
                        "(HIGH and up).")
                .defineEnum("glowHalo", Toggle.AUTO);
        VOLUMETRIC_SMOKE = b.comment(
                        "The Dusk and the Haze as real smoke with depth instead of flat puffs: HIGH lighter, ULTRA finer.",
                        "The costliest of all - turn it off first on a weak video card.")
                .defineEnum("volumetricSmoke", Toggle.AUTO);
        BUBBLE_FILM = b.comment(
                        "The soap bubbles' film worked out per pixel: rainbow swirls, a denser rim, a sharp highlight",
                        "(MEDIUM and up).")
                .defineEnum("bubbleFilm", Toggle.AUTO);
        b.pop();
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

    public static Quality quality() {
        try {
            return QUALITY.get();
        } catch (IllegalStateException notLoadedYet) {
            return Quality.HIGH;
        }
    }

    /** {@code toggle} decided: AUTO = on from {@code from} quality up. */
    private static boolean on(ForgeConfigSpec.EnumValue<Toggle> toggle, Quality from) {
        Toggle t;
        try {
            t = toggle.get();
        } catch (IllegalStateException notLoadedYet) {
            t = Toggle.AUTO;
        }
        return t == Toggle.ON || (t == Toggle.AUTO && quality().ordinal() >= from.ordinal());
    }

    /** Refraction worked out per pixel (else the plain mesh refraction). */
    public static boolean pixelDistortion() {
        return on(PIXEL_DISTORTION, Quality.MEDIUM);
    }

    /** The anomalies light up the world round them (the picture only). */
    public static boolean anomalyLight() {
        return on(ANOMALY_LIGHT, Quality.HIGH);
    }

    public static boolean softEdges() {
        return on(SOFT_EDGES, Quality.MEDIUM);
    }

    public static boolean dithering() {
        return on(DITHERING, Quality.MEDIUM);
    }

    public static boolean glowHalo() {
        return on(GLOW_HALO, Quality.HIGH);
    }

    public static boolean volumetricSmoke() {
        return on(VOLUMETRIC_SMOKE, Quality.HIGH);
    }

    /** Ray-marching steps through the smoke: finer at ULTRA. */
    public static int smokeSteps() {
        return quality() == Quality.ULTRA ? 32 : 16;
    }

    public static boolean bubbleFilm() {
        return on(BUBBLE_FILM, Quality.MEDIUM);
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
