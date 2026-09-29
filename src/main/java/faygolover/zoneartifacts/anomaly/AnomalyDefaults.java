package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.tuner.TunerKind;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-type standard values for zone anomalies (from the common config), plus display names.
 * One place for "what does a fresh Electra / Zharka / Iney start with", used when placing,
 * when migrating old saves, and by the tuners to show the standard next to the current value.
 */
public final class AnomalyDefaults {

    /** Every zone anomaly starts one block in size. */
    public static final double SIZE = 1.0;

    private AnomalyDefaults() {
    }

    /** Electra: cooldown after firing. Zharka / Iney: interval between damage pulses.
     *  Razlom: pause after a fire jet. */
    public static double cooldownSeconds(ResourceLocation typeId) {
        if (AnomalyTypeIds.ZHARKA.equals(typeId)) return ModCommonConfig.ZHARKA_INTERVAL_SECONDS.get();
        if (AnomalyTypeIds.INEY.equals(typeId)) return ModCommonConfig.INEY_INTERVAL_SECONDS.get();
        if (AnomalyTypeIds.RAZLOM.equals(typeId)) return ModCommonConfig.RAZLOM_COOLDOWN_SECONDS.get().doubleValue();
        if (AnomalyTypeIds.COLD_RAZLOM.equals(typeId)) return ModCommonConfig.COLD_RAZLOM_COOLDOWN_SECONDS.get().doubleValue();
        if (AnomalyTypeIds.PLESH.equals(typeId)) return ModCommonConfig.PLESH_COOLDOWN_SECONDS.get().doubleValue();
        if (AnomalyTypeIds.VORONKA.equals(typeId)) return ModCommonConfig.VORONKA_COOLDOWN_SECONDS.get().doubleValue();
        if (AnomalyTypeIds.KARUSEL.equals(typeId)) return ModCommonConfig.KARUSEL_COOLDOWN_SECONDS.get().doubleValue();
        if (AnomalyTypeIds.PODUSHKA.equals(typeId)) return 1.0;
        return ModCommonConfig.ELECTRA_COOLDOWN_SECONDS.get().doubleValue();
    }

    /** Smallest cooldown the tuner allows: Zharka / Iney may hit several times a second. */
    public static double minCooldownSeconds(ResourceLocation typeId) {
        return AnomalyTypeIds.isThermal(typeId) ? 0.1 : 1.0;
    }

    /** Cooldown tuner step: tenths of a second for Zharka / Iney, whole seconds otherwise. */
    public static double cooldownStep(ResourceLocation typeId, boolean sneaking) {
        if (AnomalyTypeIds.isThermal(typeId)) return sneaking ? 1.0 : 0.1;
        return TunerKind.COOLDOWN.step(sneaking);
    }

    /** Seconds to ticks, at least one tick. */
    public static int ticks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    public static float damage(ResourceLocation typeId) {
        if (AnomalyTypeIds.ZHARKA.equals(typeId)) return ModCommonConfig.ZHARKA_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.INEY.equals(typeId)) return ModCommonConfig.INEY_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.RAZLOM.equals(typeId)) return ModCommonConfig.RAZLOM_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.COLD_RAZLOM.equals(typeId)) return ModCommonConfig.COLD_RAZLOM_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.PLESH.equals(typeId)) return ModCommonConfig.PLESH_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.VORONKA.equals(typeId)) return ModCommonConfig.VORONKA_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.KARUSEL.equals(typeId)) return ModCommonConfig.KARUSEL_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.PODUSHKA.equals(typeId)) return 0.0f;
        return ModCommonConfig.ELECTRA_DAMAGE.get().floatValue();
    }

    public static int intensity(ResourceLocation typeId) {
        if (AnomalyTypeIds.ZHARKA.equals(typeId)) return ModCommonConfig.ZHARKA_INTENSITY.get();
        if (AnomalyTypeIds.INEY.equals(typeId)) return ModCommonConfig.INEY_INTENSITY.get();
        if (AnomalyTypeIds.RAZLOM.equals(typeId)) return ModCommonConfig.RAZLOM_INTENSITY.get();
        if (AnomalyTypeIds.COLD_RAZLOM.equals(typeId)) return ModCommonConfig.COLD_RAZLOM_INTENSITY.get();
        if (AnomalyTypeIds.PLESH.equals(typeId)) return ModCommonConfig.PLESH_INTENSITY.get();
        if (AnomalyTypeIds.VORONKA.equals(typeId)) return ModCommonConfig.VORONKA_INTENSITY.get();
        if (AnomalyTypeIds.KARUSEL.equals(typeId)) return ModCommonConfig.KARUSEL_INTENSITY.get();
        if (AnomalyTypeIds.PODUSHKA.equals(typeId)) return ModCommonConfig.PODUSHKA_INTENSITY.get();
        return ModCommonConfig.ELECTRA_INTENSITY.get();
    }

    /** Translation key of the anomaly's name, e.g. {@code anomaly.fl_zone_arts.zharka}. */
    public static String nameKey(ResourceLocation typeId) {
        return "anomaly.fl_zone_arts." + typeId.getPath();
    }

    /** Which tuners make sense for a zone anomaly: speed only for the gravitational ones (their
     *  force), cooldown and damage not for the harmless, always-on Podushka; targeting never. */
    public static boolean tunable(ResourceLocation typeId, TunerKind kind) {
        return switch (kind) {
            case SPEED -> AnomalyTypeIds.isGravity(typeId);
            case COOLDOWN, DAMAGE -> !AnomalyTypeIds.PODUSHKA.equals(typeId);
            case TARGETING -> false;
            default -> true;
        };
    }

    /** Translation key of what a tuner changes on this anomaly — the cooldown tuner means
     *  "damage interval" on the thermal ones. */
    public static String settingKey(ResourceLocation typeId, TunerKind kind) {
        if (kind == TunerKind.COOLDOWN && AnomalyTypeIds.isThermal(typeId)) return "tuner.fl_zone_arts.damage_interval";
        if (kind == TunerKind.SPEED && AnomalyTypeIds.isGravity(typeId)) {
            return AnomalyTypeIds.PODUSHKA.equals(typeId) ? "tuner.fl_zone_arts.bounce_height" : "tuner.fl_zone_arts.force";
        }
        return kind.translationKey();
    }
}
