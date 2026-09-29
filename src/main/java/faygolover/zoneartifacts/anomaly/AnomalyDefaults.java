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

    /** Electra: cooldown after firing. Zharka / Iney: interval between damage pulses. */
    public static int cooldownSeconds(ResourceLocation typeId) {
        if (AnomalyTypeIds.ZHARKA.equals(typeId)) return ModCommonConfig.ZHARKA_INTERVAL_SECONDS.get();
        if (AnomalyTypeIds.INEY.equals(typeId)) return ModCommonConfig.INEY_INTERVAL_SECONDS.get();
        return ModCommonConfig.ELECTRA_COOLDOWN_SECONDS.get();
    }

    public static float damage(ResourceLocation typeId) {
        if (AnomalyTypeIds.ZHARKA.equals(typeId)) return ModCommonConfig.ZHARKA_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.INEY.equals(typeId)) return ModCommonConfig.INEY_DAMAGE.get().floatValue();
        return ModCommonConfig.ELECTRA_DAMAGE.get().floatValue();
    }

    public static int intensity(ResourceLocation typeId) {
        if (AnomalyTypeIds.ZHARKA.equals(typeId)) return ModCommonConfig.ZHARKA_INTENSITY.get();
        if (AnomalyTypeIds.INEY.equals(typeId)) return ModCommonConfig.INEY_INTENSITY.get();
        return ModCommonConfig.ELECTRA_INTENSITY.get();
    }

    /** Translation key of the anomaly's name, e.g. {@code anomaly.fl_zone_arts.zharka}. */
    public static String nameKey(ResourceLocation typeId) {
        return "anomaly.fl_zone_arts." + typeId.getPath();
    }

    /** Translation key of what a tuner changes on this anomaly — the cooldown tuner means
     *  "damage interval" on the thermal ones. */
    public static String settingKey(ResourceLocation typeId, TunerKind kind) {
        if (kind == TunerKind.COOLDOWN && AnomalyTypeIds.isThermal(typeId)) return "tuner.fl_zone_arts.damage_interval";
        return kind.translationKey();
    }
}
