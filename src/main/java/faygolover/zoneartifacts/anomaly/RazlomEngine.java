package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import faygolover.zoneartifacts.network.RazlomJetPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Server side of the Razlom, called from {@link AnomalyEngine} every tick.
 * <ol>
 *     <li><b>Idle</b>: waits for a living being in its zone (not a spectator, not a player in
 *     creative) — the nearest one becomes the target.</li>
 *     <li><b>Jet</b> ({@code active}): for {@code razlom.jetSeconds} the flame burns the target:
 *     a hit every {@code hitIntervalTicks} (fire damage, sets it on fire), only while no block
 *     stands between them. The jet follows the target up to {@code jetRange} blocks beyond the
 *     zone; if it gets away or dies, the jet switches to someone else in the zone or ends early.</li>
 *     <li><b>Cooldown</b> ({@link AnomalyInstance#cooldownSeconds()}): rests, then waits again.</li>
 * </ol>
 * Clients learn about the jet from {@link RazlomJetPacket} (whom it's aimed at, for how long) and
 * the {@code active} / cooldown flags.
 */
public final class RazlomEngine {

    private RazlomEngine() {
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        if (instance.active()) {
            tickJet(level, instance);
            return;
        }
        if (instance.cooldownTicks() > 0) {
            instance.setCooldownTicks(instance.cooldownTicks() - 1);
            if (instance.cooldownTicks() == 0) AnomalySyncHandler.broadcastState(level, instance);
            return;
        }
        Vec3 flame = Razlom.flamePos(level, instance.pos(), instance.size());
        LivingEntity target = nearestInZone(level, instance, flame);
        if (target != null) startJet(level, instance, target);
    }

    private static void startJet(ServerLevel level, AnomalyInstance instance, LivingEntity target) {
        int jetTicks = Math.max(1, (int) Math.round(ModCommonConfig.RAZLOM_JET_SECONDS.get() * 20.0));
        instance.setActive(true);
        instance.setPulseTicks(jetTicks);
        // First hit almost at once, then every hitIntervalTicks.
        instance.setBlockTicks(Math.max(0, ModCommonConfig.RAZLOM_HIT_INTERVAL_TICKS.get() - 3));
        instance.setJetTargetId(target.getId());
        AnomalySyncHandler.broadcastState(level, instance);
        RazlomJetPacket.send(level, instance.pos(), target.getId(), jetTicks);
    }

    private static void tickJet(ServerLevel level, AnomalyInstance instance) {
        Vec3 flame = Razlom.flamePos(level, instance.pos(), instance.size());
        double range = Razlom.jetRange(instance.size(), ModCommonConfig.RAZLOM_JET_RANGE.get());
        int remaining = instance.pulseTicks() - 1;
        instance.setPulseTicks(remaining);

        Entity current = level.getEntity(instance.jetTargetId());
        LivingEntity target = current instanceof LivingEntity living && valid(living, flame, range) ? living : null;
        if (target == null) {
            target = nearestInZone(level, instance, flame);
            if (target == null) {
                endJet(level, instance);
                return;
            }
            instance.setJetTargetId(target.getId());
            RazlomJetPacket.send(level, instance.pos(), target.getId(), Math.max(1, remaining));
        }

        int hitTicks = instance.blockTicks() + 1;
        if (hitTicks >= ModCommonConfig.RAZLOM_HIT_INTERVAL_TICKS.get()) {
            hitTicks = 0;
            Vec3 aim = target.getBoundingBox().getCenter();
            boolean clear = level.clip(new ClipContext(flame, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target))
                    .getType() == HitResult.Type.MISS;
            if (clear) {
                // Hits land every few ticks on purpose: don't let vanilla's half-second of
                // invulnerability swallow them.
                target.invulnerableTime = 0;
                AnomalyCombat.hurt(level, target, Thermal.HEAT_DAMAGE_TYPE, instance.damage());
                int ignite = ModCommonConfig.RAZLOM_IGNITE_SECONDS.get();
                if (ignite > 0 && !target.fireImmune()) target.setSecondsOnFire(ignite);
            }
        }
        instance.setBlockTicks(hitTicks);

        if (remaining <= 0) endJet(level, instance);
    }

    private static void endJet(ServerLevel level, AnomalyInstance instance) {
        instance.setActive(false);
        instance.setPulseTicks(0);
        instance.setJetTargetId(-1);
        instance.setCooldownTicks(AnomalyDefaults.ticks(instance.cooldownSeconds()));
        AnomalySyncHandler.broadcastState(level, instance);
        RazlomJetPacket.send(level, instance.pos(), -1, 0);
    }

    @Nullable
    private static LivingEntity nearestInZone(ServerLevel level, AnomalyInstance instance, Vec3 flame) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, zone, RazlomEngine::targetable);
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity entity : inside) {
            double d = entity.getBoundingBox().getCenter().distanceToSqr(flame);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = entity;
            }
        }
        return best;
    }

    private static boolean valid(LivingEntity entity, Vec3 flame, double range) {
        return targetable(entity) && entity.getBoundingBox().getCenter().distanceToSqr(flame) <= range * range;
    }

    private static boolean targetable(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        return !(entity instanceof Player player && player.isCreative());
    }
}
