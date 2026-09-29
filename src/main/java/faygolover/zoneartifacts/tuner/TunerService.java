package faygolover.zoneartifacts.tuner;

import faygolover.zoneartifacts.anomaly.AnomalyDefaults;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import faygolover.zoneartifacts.anomaly.AnomalyTargeting;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.item.AnomalyTunerItem;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import faygolover.zoneartifacts.block.PukhBlockEntity;
import faygolover.zoneartifacts.tesla.RouteKind;
import faygolover.zoneartifacts.tesla.Tesla;
import faygolover.zoneartifacts.tesla.TeslaGeometry;
import faygolover.zoneartifacts.tesla.TeslaRoute;
import faygolover.zoneartifacts.tesla.TeslaRouteSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Applies tuner clicks, server-side. Every setting belongs to one anomaly (an Electra, or a Tesla
 * route) and is clamped to its range: the lower bounds are fixed, the upper ones come from the
 * common config. After each click the GM sees the new value and the standard value in the action
 * bar. Tesla changes apply immediately (the entity re-reads its route every tick; the respawn delay
 * takes effect at the next pop).
 */
public final class TunerService {

    private static final double MAX_CLICK_DISTANCE = AnomalyTargeting.REACH + 2.0;

    private TunerService() {
    }

    // ---- targets -------------------------------------------------------------------

    public static void tuneAnomaly(ServerPlayer player, TunerKind kind, boolean increase, boolean sneaking,
                                   ResourceLocation typeId, BlockPos pos) {
        if (!holds(player, kind)) return;
        ServerLevel level = player.serverLevel();
        AnomalySavedData data = AnomalySavedData.get(level);

        AnomalyInstance instance = null;
        for (AnomalyInstance candidate : List.copyOf(data.instances())) {
            if (candidate.pos().equals(pos) && candidate.typeId().equals(typeId)) {
                instance = candidate;
                break;
            }
        }
        if (instance == null) return;
        double reach = MAX_CLICK_DISTANCE + instance.size();
        if (player.getEyePosition().distanceToSqr(AnomalyGeometry.zoneAabb(instance).getCenter()) > reach * reach) return;

        Component name = Component.translatable(AnomalyDefaults.nameKey(typeId));
        Component setting = Component.translatable(AnomalyDefaults.settingKey(typeId, kind));
        double step = kind.step(sneaking) * (increase ? 1 : -1);
        if (!AnomalyDefaults.tunable(typeId, kind)) {
            // e.g. speed on an Electra, cooldown on the always-on Podushka.
            player.displayClientMessage(Component.translatable("message.fl_zone_arts.tuner.not_applicable",
                    name, setting), true);
            return;
        }
        String value;
        String standard;
        switch (kind) {
            case SIZE -> {
                instance.setSize(round(clamp(instance.size() + step, 1.0, ModCommonConfig.MAX_SIZE.get()), 10));
                value = fmt1(instance.size());
                standard = fmt1(AnomalyDefaults.SIZE);
            }
            case SPEED -> {
                // The gravitational anomalies' force (Podushka: bounce height).
                instance.setSpeed(round(clamp(instance.speed() + step, 0.1, ModCommonConfig.MAX_SPEED_MULTIPLIER.get()), 10));
                value = "x" + fmt1(instance.speed());
                standard = "x1.0";
            }
            case COOLDOWN -> {
                double cooldownStep = AnomalyDefaults.cooldownStep(typeId, sneaking) * (increase ? 1 : -1);
                double newSeconds = clamp(instance.cooldownSeconds() + cooldownStep,
                        AnomalyDefaults.minCooldownSeconds(typeId), ModCommonConfig.MAX_COOLDOWN_SECONDS.get());
                instance.setCooldownSeconds(round(newSeconds, 10));
                value = seconds(instance.cooldownSeconds());
                standard = seconds(AnomalyDefaults.cooldownSeconds(typeId));
            }
            case INTENSITY -> {
                instance.setIntensity((int) clamp(instance.intensity() + step, 1, ModCommonConfig.MAX_INTENSITY.get()));
                value = String.valueOf(instance.intensity());
                standard = String.valueOf(AnomalyDefaults.intensity(typeId));
            }
            case DAMAGE -> {
                instance.setDamage((float) round(clamp(instance.damage() + step, 0.0, ModCommonConfig.MAX_DAMAGE.get()), 2));
                value = fmt1(instance.damage());
                standard = fmt1(AnomalyDefaults.damage(typeId));
            }
            default -> {
                return;
            }
        }
        data.setDirty();
        AnomalySyncHandler.broadcast(level);
        report(player, name, setting, value, standard);
    }

    public static void tuneRoute(ServerPlayer player, TunerKind kind, boolean increase, boolean sneaking,
                                 int routeId, int index, BlockPos pos) {
        if (!holds(player, kind) || routeId <= 0) return;
        ServerLevel level = player.serverLevel();
        TeslaRouteSavedData data = TeslaRouteSavedData.get(level);
        TeslaRoute route = data.get(routeId);
        if (route == null || index < 0 || index >= route.waypoints().size() || !route.waypoints().get(index).equals(pos)) return;
        Vec3 point = TeslaGeometry.center(pos);
        if (player.getEyePosition().distanceToSqr(point) > MAX_CLICK_DISTANCE * MAX_CLICK_DISTANCE) return;

        RouteKind routeKind = route.kind();
        Component name = Component.translatable("message.fl_zone_arts.tuner.route",
                Component.translatable(routeKind.nameKey()), route.id());
        double step = kind.step(sneaking) * (increase ? 1 : -1);
        String value;
        String standard;
        switch (kind) {
            case SIZE -> {
                route.setSize(round(clamp(route.size() + step, 1.0, ModCommonConfig.MAX_SIZE.get()), 10));
                value = fmt1(route.size());
                standard = fmt1(Tesla.DEFAULT_SIZE);  // 1.0 for every route kind
            }
            case SPEED -> {
                route.setSpeedMultiplier(round(clamp(route.speedMultiplier() + step, 0.0, ModCommonConfig.MAX_SPEED_MULTIPLIER.get()), 10));
                value = "x" + fmt1(route.speedMultiplier());
                standard = "x1.0";
            }
            case COOLDOWN -> {
                route.setRespawnSeconds((int) clamp(route.respawnSeconds() + step, 1, ModCommonConfig.MAX_COOLDOWN_SECONDS.get()));
                value = route.respawnSeconds() + " с";
                standard = routeKind.defaultRespawnSeconds() + " с";
            }
            case INTENSITY -> {
                route.setIntensity((int) clamp(route.intensity() + step, 1, ModCommonConfig.MAX_INTENSITY.get()));
                value = String.valueOf(route.intensity());
                standard = String.valueOf(routeKind.defaultIntensity());
            }
            case DAMAGE -> {
                route.setDamage((float) round(clamp(route.damage() + step, 0.0, ModCommonConfig.MAX_DAMAGE.get()), 2));
                value = fmt1(route.damage());
                standard = fmt1(routeKind.defaultDamage());
            }
            case TARGETING -> {
                route.setChaseRadius(round(clamp(route.chaseRadius() + step, 0.0, ModCommonConfig.MAX_CHASE_RADIUS.get()), 10));
                value = route.chaseRadius() <= 0 ? "0 (не преследует)" : fmt1(route.chaseRadius()) + " бл.";
                standard = fmt1(routeKind.defaultChaseRadius()) + " бл.";
            }
            default -> {
                return;
            }
        }
        data.setDirty();
        report(player, name, Component.translatable(kind.translationKey()), value, standard);
    }

    /** Burning Fluff: size = strand length, cooldown = pause between puffs, targeting = puff range. */
    public static void tuneBlock(ServerPlayer player, TunerKind kind, boolean increase, boolean sneaking, BlockPos pos) {
        if (!holds(player, kind)) return;
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof PukhBlockEntity pukh)) return;
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > MAX_CLICK_DISTANCE * MAX_CLICK_DISTANCE) return;
        Component name = Component.translatable("block.fl_zone_arts.pukh");
        double step = kind.step(sneaking) * (increase ? 1 : -1);
        String value;
        String standard;
        String setting = kind.translationKey();
        switch (kind) {
            case SIZE -> {
                pukh.setLength(round(pukh.length() + step * 0.5, 10));
                value = fmt1(pukh.length()) + " бл.";
                standard = fmt1(ModCommonConfig.PUKH_LENGTH.get()) + " бл.";
                setting = "tuner.fl_zone_arts.strand_length";
            }
            case COOLDOWN -> {
                pukh.setCooldownSeconds(round(clamp(pukh.cooldownSeconds() + step * 0.5, 0.5, ModCommonConfig.MAX_COOLDOWN_SECONDS.get()), 10));
                value = seconds(pukh.cooldownSeconds());
                standard = seconds(ModCommonConfig.PUKH_COOLDOWN_SECONDS.get());
                setting = "tuner.fl_zone_arts.puff_cooldown";
            }
            case INTENSITY -> {
                pukh.setIntensity((int) clamp(pukh.intensity() + step, 1, ModCommonConfig.MAX_INTENSITY.get()));
                value = String.valueOf(pukh.intensity());
                standard = String.valueOf(ModCommonConfig.PUKH_INTENSITY.get());
            }
            case DAMAGE -> {
                pukh.setDamage((float) round(clamp(pukh.damage() + step, 0.0, ModCommonConfig.MAX_DAMAGE.get()), 2));
                value = fmt1(pukh.damage());
                standard = fmt1(ModCommonConfig.PUKH_DAMAGE.get());
            }
            case TARGETING -> {
                pukh.setRange(round(pukh.range() + step, 10));
                value = pukh.range() <= 0 ? "0 (не выбрасывает)" : fmt1(pukh.range()) + " бл.";
                standard = fmt1(ModCommonConfig.PUKH_RANGE.get()) + " бл.";
                setting = "tuner.fl_zone_arts.puff_range";
            }
            default -> {
                player.displayClientMessage(Component.translatable("message.fl_zone_arts.tuner.not_applicable",
                        name, Component.translatable(kind.translationKey())), true);
                return;
            }
        }
        report(player, name, Component.translatable(setting), value, standard);
    }

    // ---- helpers -------------------------------------------------------------------

    private static boolean holds(ServerPlayer player, TunerKind kind) {
        return AnomalyTunerItem.kindOf(player.getMainHandItem()) == kind
                || AnomalyTunerItem.kindOf(player.getOffhandItem()) == kind;
    }

    private static void report(ServerPlayer player, Component name, Component setting, String value, String standard) {
        player.displayClientMessage(Component.translatable("message.fl_zone_arts.tuner.value",
                name, setting, value, standard), true);
    }

    private static double clamp(double value, double min, double max) {
        return Mth.clamp(value, min, Math.max(min, max));
    }

    /** Rounds to 1/{@code per} (10 → tenths, 2 → halves) so repeated steps never drift. */
    private static double round(double value, int per) {
        return Math.round(value * per) / (double) per;
    }

    /** "5 с" for whole seconds, "0.3 с" for fractions. */
    private static String seconds(double value) {
        return (value == Math.rint(value) ? String.valueOf((long) value) : fmt1(value)) + " с";
    }

    private static String fmt1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
