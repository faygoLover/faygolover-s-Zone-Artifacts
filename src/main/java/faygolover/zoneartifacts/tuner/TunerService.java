package faygolover.zoneartifacts.tuner;

import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import faygolover.zoneartifacts.anomaly.AnomalyTargeting;
import faygolover.zoneartifacts.anomaly.Electra;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.item.AnomalyTunerItem;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
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

        Component name = Component.translatable("anomaly.fl_zone_arts.electra");
        double step = kind.step(sneaking) * (increase ? 1 : -1);
        String value;
        String standard;
        switch (kind) {
            case SIZE -> {
                instance.setSize(round(clamp(instance.size() + step, 1.0, ModCommonConfig.MAX_SIZE.get()), 10));
                value = fmt1(instance.size());
                standard = fmt1(Electra.DEFAULT_SIZE);
            }
            case SPEED -> {
                player.displayClientMessage(Component.translatable("message.fl_zone_arts.tuner.not_applicable",
                        name, Component.translatable(kind.translationKey())), true);
                return;
            }
            case COOLDOWN -> {
                instance.setCooldownSeconds((int) clamp(instance.cooldownSeconds() + step, 1, ModCommonConfig.MAX_COOLDOWN_SECONDS.get()));
                value = instance.cooldownSeconds() + " с";
                standard = ModCommonConfig.ELECTRA_COOLDOWN_SECONDS.get() + " с";
            }
            case INTENSITY -> {
                instance.setIntensity((int) clamp(instance.intensity() + step, 1, ModCommonConfig.MAX_INTENSITY.get()));
                value = String.valueOf(instance.intensity());
                standard = String.valueOf(ModCommonConfig.ELECTRA_INTENSITY.get());
            }
            case DAMAGE -> {
                instance.setDamage((float) round(clamp(instance.damage() + step, 0.0, ModCommonConfig.MAX_DAMAGE.get()), 2));
                value = fmt1(instance.damage());
                standard = fmt1(ModCommonConfig.ELECTRA_DAMAGE.get());
            }
            default -> {
                return;
            }
        }
        data.setDirty();
        AnomalySyncHandler.broadcast(level);
        report(player, name, kind, value, standard);
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

        Component name = Component.translatable("message.fl_zone_arts.tuner.tesla_route", route.id());
        double step = kind.step(sneaking) * (increase ? 1 : -1);
        String value;
        String standard;
        switch (kind) {
            case SIZE -> {
                route.setSize(round(clamp(route.size() + step, 1.0, ModCommonConfig.MAX_SIZE.get()), 10));
                value = fmt1(route.size());
                standard = fmt1(Tesla.DEFAULT_SIZE);
            }
            case SPEED -> {
                route.setSpeedMultiplier(round(clamp(route.speedMultiplier() + step, 0.0, ModCommonConfig.MAX_SPEED_MULTIPLIER.get()), 10));
                value = "x" + fmt1(route.speedMultiplier());
                standard = "x1.0";
            }
            case COOLDOWN -> {
                route.setRespawnSeconds((int) clamp(route.respawnSeconds() + step, 1, ModCommonConfig.MAX_COOLDOWN_SECONDS.get()));
                value = route.respawnSeconds() + " с";
                standard = ModCommonConfig.TESLA_RESPAWN_SECONDS.get() + " с";
            }
            case INTENSITY -> {
                route.setIntensity((int) clamp(route.intensity() + step, 1, ModCommonConfig.MAX_INTENSITY.get()));
                value = String.valueOf(route.intensity());
                standard = String.valueOf(ModCommonConfig.TESLA_INTENSITY.get());
            }
            case DAMAGE -> {
                route.setDamage((float) round(clamp(route.damage() + step, 0.0, ModCommonConfig.MAX_DAMAGE.get()), 2));
                value = fmt1(route.damage());
                standard = fmt1(ModCommonConfig.TESLA_DAMAGE.get());
            }
            default -> {
                return;
            }
        }
        data.setDirty();
        report(player, name, kind, value, standard);
    }

    // ---- helpers -------------------------------------------------------------------

    private static boolean holds(ServerPlayer player, TunerKind kind) {
        return AnomalyTunerItem.kindOf(player.getMainHandItem()) == kind
                || AnomalyTunerItem.kindOf(player.getOffhandItem()) == kind;
    }

    private static void report(ServerPlayer player, Component name, TunerKind kind, String value, String standard) {
        player.displayClientMessage(Component.translatable("message.fl_zone_arts.tuner.value",
                name, Component.translatable(kind.translationKey()), value, standard), true);
    }

    private static double clamp(double value, double min, double max) {
        return Mth.clamp(value, min, Math.max(min, max));
    }

    /** Rounds to 1/{@code per} (10 → tenths, 2 → halves) so repeated steps never drift. */
    private static double round(double value, int per) {
        return Math.round(value * per) / (double) per;
    }

    private static String fmt1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
