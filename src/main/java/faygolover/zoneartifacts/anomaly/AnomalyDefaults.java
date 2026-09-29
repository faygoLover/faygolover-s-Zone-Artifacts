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

    /** Size of a newly placed zone: one block, but the wide field anomalies start bigger. */
    public static double size(ResourceLocation typeId) {
        if (AnomalyTypeIds.SWAMP.equals(typeId)) return ModCommonConfig.SWAMP_SIZE.get();
        if (AnomalyTypeIds.DYMKA.equals(typeId)) return ModCommonConfig.DYMKA_SIZE.get();
        if (AnomalyTypeIds.SUMRAK.equals(typeId)) return ModCommonConfig.SUMRAK_SIZE.get();
        if (AnomalyTypeIds.PSI.equals(typeId)) return ModCommonConfig.PSI_SIZE.get();
        if (AnomalyTypeIds.POPPY.equals(typeId)) return ModCommonConfig.POPPY_SIZE.get();
        if (AnomalyTypeIds.RUST.equals(typeId)) return ModCommonConfig.RUST_SIZE.get();
        return SIZE;
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
        if (AnomalyTypeIds.PODUSHKA.equals(typeId) || AnomalyTypeIds.LIFT.equals(typeId)) return 1.0;
        if (AnomalyTypeIds.AMOEBA.equals(typeId)) return ModCommonConfig.AMOEBA_COOLDOWN_SECONDS.get().doubleValue();
        if (AnomalyTypeIds.KISEL.equals(typeId)) return ModCommonConfig.KISEL_INTERVAL_SECONDS.get();
        if (AnomalyTypeIds.ACID_FOG.equals(typeId)) return ModCommonConfig.FOG_JET_SECONDS.get();
        if (AnomalyTypeIds.SWAMP.equals(typeId)) return ModCommonConfig.SWAMP_DAMAGE_INTERVAL.get();
        if (AnomalyTypeIds.DYMKA.equals(typeId) || AnomalyTypeIds.SUMRAK.equals(typeId)) return 1.0;
        if (AnomalyTypeIds.PSI.equals(typeId)) return ModCommonConfig.PSI_WAVE_SECONDS.get();
        if (AnomalyTypeIds.POPPY.equals(typeId)) return ModCommonConfig.POPPY_EPISODE_INTERVAL.get();
        if (AnomalyTypeIds.RUST.equals(typeId)) return ModCommonConfig.RUST_CHARGE_SECONDS.get();
        return ModCommonConfig.ELECTRA_COOLDOWN_SECONDS.get().doubleValue();
    }

    /** Smallest cooldown the tuner allows: Zharka / Iney may hit several times a second. */
    public static double minCooldownSeconds(ResourceLocation typeId) {
        return AnomalyTypeIds.isThermal(typeId) || AnomalyTypeIds.KISEL.equals(typeId) || AnomalyTypeIds.SWAMP.equals(typeId) ? 0.1 : 1.0;
    }

    /** Cooldown tuner step: tenths of a second for Zharka / Iney, whole seconds otherwise. */
    public static double cooldownStep(ResourceLocation typeId, boolean sneaking) {
        if (AnomalyTypeIds.isThermal(typeId) || AnomalyTypeIds.KISEL.equals(typeId) || AnomalyTypeIds.SWAMP.equals(typeId)) return sneaking ? 1.0 : 0.1;
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
        if (AnomalyTypeIds.PODUSHKA.equals(typeId) || AnomalyTypeIds.LIFT.equals(typeId)) return 0.0f;
        if (AnomalyTypeIds.AMOEBA.equals(typeId)) return ModCommonConfig.AMOEBA_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.KISEL.equals(typeId)) return ModCommonConfig.KISEL_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.ACID_FOG.equals(typeId)) return ModCommonConfig.FOG_JET_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.SWAMP.equals(typeId)) return ModCommonConfig.SWAMP_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.DYMKA.equals(typeId) || AnomalyTypeIds.SUMRAK.equals(typeId) || AnomalyTypeIds.PSI.equals(typeId)) return 0.0f;
        if (AnomalyTypeIds.POPPY.equals(typeId)) return ModCommonConfig.POPPY_SLEEP_DAMAGE.get().floatValue();
        if (AnomalyTypeIds.RUST.equals(typeId)) return ModCommonConfig.RUST_DUST_DAMAGE.get().floatValue();
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
        if (AnomalyTypeIds.LIFT.equals(typeId)) return ModCommonConfig.LIFT_INTENSITY.get();
        if (AnomalyTypeIds.AMOEBA.equals(typeId)) return ModCommonConfig.AMOEBA_INTENSITY.get();
        if (AnomalyTypeIds.KISEL.equals(typeId)) return ModCommonConfig.KISEL_INTENSITY.get();
        if (AnomalyTypeIds.ACID_FOG.equals(typeId)) return ModCommonConfig.FOG_INTENSITY.get();
        if (AnomalyTypeIds.SWAMP.equals(typeId)) return ModCommonConfig.SWAMP_INTENSITY.get();
        if (AnomalyTypeIds.DYMKA.equals(typeId)) return ModCommonConfig.DYMKA_INTENSITY.get();
        if (AnomalyTypeIds.SUMRAK.equals(typeId)) return ModCommonConfig.SUMRAK_INTENSITY.get();
        if (AnomalyTypeIds.PSI.equals(typeId)) return ModCommonConfig.PSI_INTENSITY.get();
        if (AnomalyTypeIds.POPPY.equals(typeId)) return ModCommonConfig.POPPY_MAX_EPISODES.get();
        if (AnomalyTypeIds.RUST.equals(typeId)) return ModCommonConfig.RUST_INTENSITY.get();
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
            case SPEED -> AnomalyTypeIds.isGravity(typeId) || AnomalyTypeIds.LIFT.equals(typeId) || AnomalyTypeIds.ACID_FOG.equals(typeId)
                    || AnomalyTypeIds.SWAMP.equals(typeId);
            case COOLDOWN -> !AnomalyTypeIds.PODUSHKA.equals(typeId) && !AnomalyTypeIds.LIFT.equals(typeId)
                    && !AnomalyTypeIds.DYMKA.equals(typeId) && !AnomalyTypeIds.SUMRAK.equals(typeId);
            case DAMAGE -> !AnomalyTypeIds.PODUSHKA.equals(typeId) && !AnomalyTypeIds.LIFT.equals(typeId)
                    && !AnomalyTypeIds.DYMKA.equals(typeId) && !AnomalyTypeIds.SUMRAK.equals(typeId) && !AnomalyTypeIds.PSI.equals(typeId);
            case TARGETING -> false;
            default -> true;
        };
    }

    /** Translation key of what a tuner changes on this anomaly — the cooldown tuner means
     *  "damage interval" on the thermal ones. */
    public static String settingKey(ResourceLocation typeId, TunerKind kind) {
        if (kind == TunerKind.COOLDOWN && (AnomalyTypeIds.isThermal(typeId) || AnomalyTypeIds.KISEL.equals(typeId))) return "tuner.fl_zone_arts.damage_interval";
        if (kind == TunerKind.COOLDOWN && AnomalyTypeIds.ACID_FOG.equals(typeId)) return "tuner.fl_zone_arts.jet_interval";
        if (kind == TunerKind.SPEED && AnomalyTypeIds.LIFT.equals(typeId)) return "tuner.fl_zone_arts.push_out";
        if (AnomalyTypeIds.SWAMP.equals(typeId)) {
            if (kind == TunerKind.COOLDOWN) return "tuner.fl_zone_arts.damage_interval";
            if (kind == TunerKind.SPEED) return "tuner.fl_zone_arts.sink_speed";
        }
        if (kind == TunerKind.INTENSITY && (AnomalyTypeIds.DYMKA.equals(typeId) || AnomalyTypeIds.SUMRAK.equals(typeId))) return "tuner.fl_zone_arts.density";
        if (AnomalyTypeIds.PSI.equals(typeId)) {
            if (kind == TunerKind.INTENSITY) return "tuner.fl_zone_arts.psi_strength";
            if (kind == TunerKind.COOLDOWN) return "tuner.fl_zone_arts.wave_interval";
        }
        if (AnomalyTypeIds.POPPY.equals(typeId)) {
            if (kind == TunerKind.COOLDOWN) return "tuner.fl_zone_arts.episode_interval";
            if (kind == TunerKind.INTENSITY) return "tuner.fl_zone_arts.max_episodes";
            if (kind == TunerKind.DAMAGE) return "tuner.fl_zone_arts.sleep_damage";
        }
        if (AnomalyTypeIds.RUST.equals(typeId)) {
            if (kind == TunerKind.COOLDOWN) return "tuner.fl_zone_arts.charge_interval";
            if (kind == TunerKind.DAMAGE) return "tuner.fl_zone_arts.dust_damage";
        }
        if (kind == TunerKind.SPEED && AnomalyTypeIds.ACID_FOG.equals(typeId)) return "tuner.fl_zone_arts.jet_frequency";
        if (kind == TunerKind.SPEED && AnomalyTypeIds.isGravity(typeId)) {
            return AnomalyTypeIds.PODUSHKA.equals(typeId) ? "tuner.fl_zone_arts.bounce_height" : "tuner.fl_zone_arts.force";
        }
        return kind.translationKey();
    }
}
