package faygolover.zoneartifacts.pda;

import faygolover.zoneartifacts.anomaly.AnomalyDefaults;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import faygolover.zoneartifacts.anomaly.AnomalyTargeting;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Pukh;
import faygolover.zoneartifacts.anomaly.Swamp;
import faygolover.zoneartifacts.anomaly.WebSavedData;
import faygolover.zoneartifacts.block.EzhikBlockEntity;
import faygolover.zoneartifacts.block.PukhBlockEntity;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.item.PdaItem;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import faygolover.zoneartifacts.network.PdaDataPacket;
import faygolover.zoneartifacts.network.SyncWebsPacket;
import faygolover.zoneartifacts.tesla.RouteKind;
import faygolover.zoneartifacts.tesla.Tesla;
import faygolover.zoneartifacts.tesla.TeslaGeometry;
import faygolover.zoneartifacts.tesla.TeslaRoute;
import faygolover.zoneartifacts.tesla.TeslaRouteSavedData;
import faygolover.zoneartifacts.tuner.TunerKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * The KPK, server-side: what can be set on each kind of anomaly (one list of settings, used both to
 * show the window and to apply it, so the two never disagree), and applying a window's values —
 * clamped to their ranges, whatever the client sent. Anyone holding a KPK may use it (like the
 * tuners it replaces: it only comes from the creative tab).
 */
public final class PdaService {

    private static final double MAX_DISTANCE = AnomalyTargeting.REACH + 2.0;

    private PdaService() {
    }

    /** One setting: how to read it, how to write it, its range and standard. */
    private record Spec(String id, String label, double min, double max, double step, double standard,
                        DoubleSupplier get, DoubleConsumer set) {
        PdaParam param() {
            return new PdaParam(id, label, min, max, step, get.getAsDouble(), standard);
        }
    }

    /** What the target is, as found on the server now. */
    private record Found(Component title, List<Spec> specs, @Nullable AnomalyInstance zone, Runnable saved) {
    }

    // ---- requests ------------------------------------------------------------------------------

    public static void open(ServerPlayer player, PdaTarget target) {
        if (!PdaItem.holds(player)) return;
        Found found = find(player, target);
        if (found == null) {
            player.displayClientMessage(Component.translatable("message.fl_zone_arts.pda.lost"), true);
            return;
        }
        PdaDataPacket.send(player, view(target, found));
    }

    /**
     * The window's values ({@code values} by setting id), the switches (-1: leave them), a move by
     * whole blocks, or {@code reset}: every setting back to its standard.
     */
    public static void apply(ServerPlayer player, PdaTarget target, Map<String, Double> values, int switches,
                             int dx, int dy, int dz, boolean reset) {
        if (!PdaItem.holds(player)) return;
        Found found = find(player, target);
        if (found == null) {
            player.displayClientMessage(Component.translatable("message.fl_zone_arts.pda.lost"), true);
            return;
        }
        for (Spec spec : found.specs()) {
            Double v = reset ? Double.valueOf(spec.standard()) : values.get(spec.id());
            if (v == null || v.isNaN()) continue;
            spec.set().accept(spec.param().snap(v));
        }
        PdaTarget now = target;
        AnomalyInstance zone = found.zone();
        if (zone != null) {
            if (switches >= 0) {
                // Only the switches this kind has; the others keep what they were.
                int mask = AnomalyDefaults.switchMask(zone.typeId());
                zone.setSwitchBits((zone.switchBits() & ~mask) | (switches & mask));
            }
            if (dx != 0 || dy != 0 || dz != 0) {
                BlockPos to = zone.pos().offset(Integer.signum(dx), Integer.signum(dy), Integer.signum(dz));
                if (free(player.serverLevel(), zone, to)) {
                    zone.setPos(to);
                    now = PdaTarget.zone(zone.typeId(), to);
                } else {
                    player.displayClientMessage(Component.translatable("message.fl_zone_arts.pda.occupied"), true);
                }
            }
        }
        found.saved().run();
        Found fresh = find(player, now);
        if (fresh != null) PdaDataPacket.send(player, view(now, fresh));
    }

    /** Removes the anomaly altogether (a zone, a whole route, the block, a whole web). */
    public static void delete(ServerPlayer player, PdaTarget target) {
        if (!PdaItem.holds(player)) return;
        Found found = find(player, target);
        if (found == null) {
            player.displayClientMessage(Component.translatable("message.fl_zone_arts.pda.lost"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        switch (target.kind()) {
            case PdaTarget.ZONE -> {
                AnomalyInstance zone = found.zone();
                if (zone == null) return;
                faygolover.zoneartifacts.anomaly.GravityEngine.forget(level, zone);
                faygolover.zoneartifacts.anomaly.LiftEngine.forget(zone);
                faygolover.zoneartifacts.anomaly.AmoebaEngine.forget(zone);
                AnomalySavedData.get(level).remove(zone);
                Swamp.invalidate(level);
                AnomalySyncHandler.broadcast(level);
            }
            case PdaTarget.ROUTE -> {
                TeslaRoute route = TeslaRouteSavedData.get(level).get(target.id());
                if (route != null) faygolover.zoneartifacts.tesla.TeslaRouteService.removeRoute(level, route);
            }
            case PdaTarget.BLOCK -> level.removeBlock(target.pos(), false);
            case PdaTarget.WEB -> {
                WebSavedData data = WebSavedData.get(level);
                data.remove(target.id());
                data.setDirty();
                SyncWebsPacket.broadcast(level);
            }
            default -> {
                return;
            }
        }
        player.displayClientMessage(Component.translatable("message.fl_zone_arts.pda.deleted", found.title()), true);
    }

    private static PdaView view(PdaTarget target, Found found) {
        List<PdaParam> params = new ArrayList<>();
        for (Spec spec : found.specs()) params.add(spec.param());
        AnomalyInstance zone = found.zone();
        return new PdaView(target, found.title(), params, zone != null ? zone.switchBits() : -1,
                zone != null ? AnomalyDefaults.switchMask(zone.typeId()) : 0, zone != null);
    }

    private static boolean free(ServerLevel level, AnomalyInstance moving, BlockPos to) {
        for (AnomalyInstance other : AnomalySavedData.get(level).instances()) {
            if (other != moving && other.pos().equals(to) && other.typeId().equals(moving.typeId())) return false;
        }
        return true;
    }

    // ---- targets -------------------------------------------------------------------------------

    @Nullable
    private static Found find(ServerPlayer player, PdaTarget target) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        switch (target.kind()) {
            case PdaTarget.ZONE -> {
                if (target.typeId() == null) return null;
                AnomalySavedData data = AnomalySavedData.get(level);
                for (AnomalyInstance instance : data.instances()) {
                    if (!instance.pos().equals(target.pos()) || !instance.typeId().equals(target.typeId())) continue;
                    double reach = MAX_DISTANCE + instance.size();
                    if (eye.distanceToSqr(AnomalyGeometry.zoneAabb(instance).getCenter()) > reach * reach) return null;
                    return new Found(Component.translatable(AnomalyDefaults.nameKey(instance.typeId())), zoneSpecs(instance), instance,
                            () -> {
                                data.setDirty();
                                Swamp.invalidate(level);
                                AnomalySyncHandler.broadcast(level);
                            });
                }
                return null;
            }
            case PdaTarget.ROUTE -> {
                TeslaRouteSavedData data = TeslaRouteSavedData.get(level);
                TeslaRoute route = data.get(target.id());
                if (route == null) return null;
                if (eye.distanceToSqr(TeslaGeometry.center(target.pos())) > MAX_DISTANCE * MAX_DISTANCE * 4) return null;
                Component title = Component.translatable("message.fl_zone_arts.tuner.route",
                        Component.translatable(route.kind().nameKey()), route.id());
                return new Found(title, routeSpecs(route), null, data::setDirty);
            }
            case PdaTarget.BLOCK -> {
                BlockPos pos = target.pos();
                if (!level.isLoaded(pos) || eye.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE * MAX_DISTANCE * 4) return null;
                if (level.getBlockEntity(pos) instanceof EzhikBlockEntity ezhik) {
                    return new Found(Component.translatable("block.fl_zone_arts.ezhik"), ezhikSpecs(ezhik), null, () -> {
                    });
                }
                if (level.getBlockEntity(pos) instanceof PukhBlockEntity pukh) {
                    return new Found(Component.translatable("block.fl_zone_arts.pukh"), pukhSpecs(pukh), null, () -> {
                    });
                }
                return null;
            }
            case PdaTarget.WEB -> {
                WebSavedData data = WebSavedData.get(level);
                WebSavedData.Web web = data.get(target.id());
                if (web == null || web.distanceSqTo(eye) > MAX_DISTANCE * MAX_DISTANCE * 4) return null;
                return new Found(Component.translatable("anomaly.fl_zone_arts.pautina"), webSpecs(web), null, () -> {
                    data.setDirty();
                    SyncWebsPacket.broadcast(level);
                });
            }
            default -> {
                return null;
            }
        }
    }

    // ---- what each has ---------------------------------------------------------------------------

    private static List<Spec> zoneSpecs(AnomalyInstance z) {
        ResourceLocation type = z.typeId();
        List<Spec> out = new ArrayList<>();
        double maxSize = ModCommonConfig.MAX_SIZE.get();
        double std = AnomalyDefaults.size(type);
        if (AnomalyDefaults.boxShaped(type)) {
            out.add(new Spec("size_x", "pda.fl_zone_arts.width", 1.0, maxSize, 0.1, std, z::sizeX,
                    v -> z.setDimensions(v, z.sizeY(), z.sizeZ())));
            out.add(new Spec("size_y", AnomalyTypeIds.SWAMP.equals(type) ? "pda.fl_zone_arts.depth" : "pda.fl_zone_arts.height",
                    1.0, maxSize, 0.1, std, z::sizeY, v -> z.setDimensions(z.sizeX(), v, z.sizeZ())));
            out.add(new Spec("size_z", "pda.fl_zone_arts.length", 1.0, maxSize, 0.1, std, z::sizeZ,
                    v -> z.setDimensions(z.sizeX(), z.sizeY(), v)));
        } else {
            out.add(new Spec("size", AnomalyDefaults.settingKey(type, TunerKind.SIZE), 1.0, maxSize, 0.1, std, z::size, v -> {
                z.clearDimensions();
                z.setSize(v);
            }));
        }
        if (AnomalyDefaults.tunable(type, TunerKind.SPEED)) {
            double min = AnomalyTypeIds.LIFT.equals(type) || AnomalyTypeIds.ACID_FOG.equals(type) ? 0.0 : 0.1;
            out.add(new Spec("speed", AnomalyDefaults.settingKey(type, TunerKind.SPEED), min, ModCommonConfig.MAX_SPEED_MULTIPLIER.get(), 0.1,
                    1.0, z::speed, z::setSpeed));
        }
        if (AnomalyDefaults.tunable(type, TunerKind.COOLDOWN)) {
            out.add(new Spec("cooldown", AnomalyDefaults.settingKey(type, TunerKind.COOLDOWN), AnomalyDefaults.minCooldownSeconds(type),
                    ModCommonConfig.MAX_COOLDOWN_SECONDS.get(), AnomalyDefaults.cooldownStep(type, false),
                    AnomalyDefaults.cooldownSeconds(type), z::cooldownSeconds, z::setCooldownSeconds));
        }
        if (AnomalyDefaults.tunable(type, TunerKind.INTENSITY)) {
            out.add(new Spec("intensity", AnomalyDefaults.settingKey(type, TunerKind.INTENSITY), 1, ModCommonConfig.MAX_INTENSITY.get(), 1,
                    AnomalyDefaults.intensity(type), z::intensity, v -> z.setIntensity((int) Math.round(v))));
        }
        if (AnomalyDefaults.tunable(type, TunerKind.DAMAGE)) {
            out.add(new Spec("damage", AnomalyDefaults.settingKey(type, TunerKind.DAMAGE), 0.0, ModCommonConfig.MAX_DAMAGE.get(), 0.5,
                    AnomalyDefaults.damage(type), z::damage, v -> z.setDamage((float) v)));
        }
        if (AnomalyTypeIds.RUST.equals(type)) {
            // Rust: every how many raised puffs of dust one is charged (a count, not a distance).
            out.add(new Spec("range", AnomalyDefaults.settingKey(type, TunerKind.TARGETING), 1, 200, 1,
                    AnomalyDefaults.range(type), () -> z.range() > 0 ? z.range() : AnomalyDefaults.range(type), z::setRange));
        } else if (AnomalyDefaults.tunable(type, TunerKind.TARGETING)) {
            out.add(new Spec("range", AnomalyDefaults.settingKey(type, TunerKind.TARGETING), 0.5, 64.0, 0.5,
                    AnomalyDefaults.range(type), z::range, z::setRange));
        }
        return out;
    }

    private static List<Spec> routeSpecs(TeslaRoute r) {
        RouteKind kind = r.kind();
        List<Spec> out = new ArrayList<>();
        out.add(new Spec("size", "tuner.fl_zone_arts.size", 1.0, ModCommonConfig.MAX_SIZE.get(), 0.1, Tesla.DEFAULT_SIZE, r::size, r::setSize));
        out.add(new Spec("speed", "tuner.fl_zone_arts.speed", 0.0, ModCommonConfig.MAX_SPEED_MULTIPLIER.get(), 0.1, 1.0,
                r::speedMultiplier, r::setSpeedMultiplier));
        out.add(new Spec("respawn", "pda.fl_zone_arts.respawn", 1, ModCommonConfig.MAX_COOLDOWN_SECONDS.get(), 1,
                kind.defaultRespawnSeconds(), r::respawnSeconds, v -> r.setRespawnSeconds((int) Math.round(v))));
        out.add(new Spec("intensity", "tuner.fl_zone_arts.intensity", 1, ModCommonConfig.MAX_INTENSITY.get(), 1,
                kind.defaultIntensity(), r::intensity, v -> r.setIntensity((int) Math.round(v))));
        out.add(new Spec("damage", "tuner.fl_zone_arts.damage", 0.0, ModCommonConfig.MAX_DAMAGE.get(), 0.5,
                kind.defaultDamage(), r::damage, v -> r.setDamage((float) v)));
        out.add(new Spec("chase", "pda.fl_zone_arts.chase", 0.0, ModCommonConfig.MAX_CHASE_RADIUS.get(), 0.5,
                kind.defaultChaseRadius(), r::chaseRadius, r::setChaseRadius));
        return out;
    }

    private static List<Spec> pukhSpecs(PukhBlockEntity p) {
        List<Spec> out = new ArrayList<>();
        out.add(new Spec("length", "tuner.fl_zone_arts.strand_length", Pukh.MIN_LENGTH, Pukh.MAX_LENGTH, 0.5,
                ModCommonConfig.PUKH_LENGTH.get(), p::length, p::setLength));
        out.add(new Spec("cooldown", "tuner.fl_zone_arts.puff_cooldown", 0.5, ModCommonConfig.MAX_COOLDOWN_SECONDS.get(), 0.5,
                ModCommonConfig.PUKH_COOLDOWN_SECONDS.get(), p::cooldownSeconds, p::setCooldownSeconds));
        out.add(new Spec("intensity", "tuner.fl_zone_arts.intensity", 1, ModCommonConfig.MAX_INTENSITY.get(), 1,
                ModCommonConfig.PUKH_INTENSITY.get(), p::intensity, v -> p.setIntensity((int) Math.round(v))));
        out.add(new Spec("damage", "tuner.fl_zone_arts.damage", 0.0, ModCommonConfig.MAX_DAMAGE.get(), 0.5,
                ModCommonConfig.PUKH_DAMAGE.get(), p::damage, v -> p.setDamage((float) v)));
        out.add(new Spec("range", "tuner.fl_zone_arts.puff_range", 0.0, Pukh.MAX_RANGE, 0.5,
                ModCommonConfig.PUKH_RANGE.get(), p::range, p::setRange));
        return out;
    }

    private static List<Spec> ezhikSpecs(EzhikBlockEntity e) {
        List<Spec> out = new ArrayList<>();
        out.add(new Spec("radius", "pda.fl_zone_arts.ezhik_size", EzhikBlockEntity.MIN_RADIUS, EzhikBlockEntity.MAX_RADIUS, 0.05,
                ModCommonConfig.EZHIK_SIZE.get(), e::radius, e::setRadius));
        out.add(new Spec("intensity", "pda.fl_zone_arts.ezhik_count", 1, ModCommonConfig.MAX_INTENSITY.get(), 1,
                ModCommonConfig.EZHIK_INTENSITY.get(), e::intensity, v -> e.setIntensity((int) Math.round(v))));
        out.add(new Spec("speed", "tuner.fl_zone_arts.speed", 0.0, ModCommonConfig.MAX_SPEED_MULTIPLIER.get(), 0.1, 1.0,
                e::speed, e::setSpeed));
        out.add(new Spec("depth", "pda.fl_zone_arts.ezhik_depth", EzhikBlockEntity.MIN_DEPTH, EzhikBlockEntity.MAX_DEPTH, 0.05,
                EzhikBlockEntity.DEFAULT_DEPTH, e::depth, e::setDepth));
        return out;
    }

    private static List<Spec> webSpecs(WebSavedData.Web w) {
        List<Spec> out = new ArrayList<>();
        out.add(new Spec("damage", "tuner.fl_zone_arts.damage", 0.0, ModCommonConfig.MAX_DAMAGE.get(), 0.5,
                ModCommonConfig.WEB_DAMAGE.get(), () -> w.damage, v -> w.damage = (float) v));
        out.add(new Spec("regrow", "tuner.fl_zone_arts.regrow", 1.0, ModCommonConfig.MAX_COOLDOWN_SECONDS.get(), 1.0,
                ModCommonConfig.WEB_REGROW_SECONDS.get(), () -> w.regrowSeconds, v -> w.regrowSeconds = v));
        out.add(new Spec("intensity", "tuner.fl_zone_arts.glint", 1, ModCommonConfig.MAX_INTENSITY.get(), 1,
                ModCommonConfig.WEB_INTENSITY.get(), () -> w.intensity, v -> w.intensity = (int) Math.round(v)));
        return out;
    }
}
