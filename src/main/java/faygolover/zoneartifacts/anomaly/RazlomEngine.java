package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import faygolover.zoneartifacts.network.RazlomJetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Server side of the Razlom, called from {@link AnomalyEngine} every tick.
 * <ol>
 *     <li><b>Idle</b>: waits for a living being in its zone (not a spectator, not a player in
 *     creative) — the nearest one becomes the target. A flying projectile (a snowball, an arrow...)
 *     sets it off too, like the Electra: the jet chases it, it flies off, the jet dies out and the
 *     Razlom goes on cooldown — a way to discharge it. Projectiles take no damage.</li>
 *     <li><b>Jet</b> ({@code active}): for {@code razlom.jetSeconds} the flame burns the target:
 *     a hit every {@code hitIntervalTicks} (fire damage, sets it on fire), only while no block
 *     stands in the way of the jet's arc ({@link Razlom#jetPoint}). Now and then
 *     ({@code blockIgniteChance}) a hit also sets a spot next to the target on fire, or the block
 *     in the way when something blocks it. The jet follows the target up to {@code jetRange} blocks beyond the
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
        Entity target = nearestInZone(level, instance, flame);
        if (target != null) startJet(level, instance, target);
    }

    private static void startJet(ServerLevel level, AnomalyInstance instance, Entity target) {
        int jetTicks = Math.max(1, (int) Math.round(ModCommonConfig.RAZLOM_JET_SECONDS.get() * 20.0));
        instance.setActive(true);
        instance.setPulseTicks(jetTicks);
        firstHitOnArrival(instance);
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
        Entity target = current != null && valid(current, flame, range) ? current : null;
        if (target == null) {
            target = nearestInZone(level, instance, flame);
            if (target == null) {
                endJet(level, instance);
                return;
            }
            instance.setJetTargetId(target.getId());
            firstHitOnArrival(instance); // the jet shoots out at the new target again
            RazlomJetPacket.send(level, instance.pos(), target.getId(), Math.max(1, remaining));
        }

        int hitTicks = instance.blockTicks() + 1;
        if (hitTicks >= ModCommonConfig.RAZLOM_HIT_INTERVAL_TICKS.get()) {
            hitTicks = 0;
            Vec3 aim = target.getBoundingBox().getCenter();
            BlockHitResult blocked = blockAlongArc(level, flame, aim, target);
            double blockChance = ModCommonConfig.RAZLOM_BLOCK_IGNITE_CHANCE.get();
            if (blocked == null && target instanceof LivingEntity living) {
                // Hits land every few ticks on purpose: don't let vanilla's half-second of
                // invulnerability swallow them.
                living.invulnerableTime = 0;
                AnomalyCombat.hurt(level, living, Thermal.HEAT_DAMAGE_TYPE, instance.damage());
                int ignite = ModCommonConfig.RAZLOM_IGNITE_SECONDS.get();
                if (ignite > 0 && !living.fireImmune()) living.setSecondsOnFire(ignite);
                if (level.random.nextDouble() < blockChance) igniteNear(level, living.blockPosition());
            } else if (blocked != null && level.random.nextDouble() < blockChance) {
                // (A projectile it reaches is only burned at, not hurt.)
                // The block in the way catches fire (on the side the jet came from).
                igniteSpot(level, blocked.getBlockPos().relative(blocked.getDirection()));
            }
        }
        instance.setBlockTicks(hitTicks);

        if (remaining <= 0) endJet(level, instance);
    }

    /** Counts the hit timer so the first hit lands as the jet arrives ({@link Razlom#JET_GROW_TICKS}). */
    private static void firstHitOnArrival(AnomalyInstance instance) {
        instance.setBlockTicks(Math.max(0, ModCommonConfig.RAZLOM_HIT_INTERVAL_TICKS.get() - Razlom.JET_GROW_TICKS));
    }

    private static void endJet(ServerLevel level, AnomalyInstance instance) {
        instance.setActive(false);
        instance.setPulseTicks(0);
        instance.setJetTargetId(-1);
        instance.setCooldownTicks(AnomalyDefaults.ticks(instance.cooldownSeconds()));
        AnomalySyncHandler.broadcastState(level, instance);
        RazlomJetPacket.send(level, instance.pos(), -1, 0);
    }

    /** First block the jet's arc runs into, or null if it reaches {@code aim}. */
    @Nullable
    private static BlockHitResult blockAlongArc(ServerLevel level, Vec3 flame, Vec3 aim, Entity context) {
        Vec3 prev = flame;
        for (int i = 1; i <= Razlom.JET_SEGMENTS; i++) {
            Vec3 next = Razlom.jetPoint(flame, aim, i / (double) Razlom.JET_SEGMENTS);
            BlockHitResult hit = level.clip(new ClipContext(prev, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
            if (hit.getType() != HitResult.Type.MISS) return hit;
            prev = next;
        }
        return null;
    }

    /** A fire on a free spot right around {@code feet} (a few tries). */
    private static void igniteNear(ServerLevel level, BlockPos feet) {
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos pos = feet.offset(level.random.nextInt(3) - 1, 0, level.random.nextInt(3) - 1);
            if (igniteSpot(level, pos)) return;
        }
    }

    private static boolean igniteSpot(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) return false;
        if (!BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) return false;
        level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
        return true;
    }

    /** Nearest living being in the zone; if there is none, the nearest flying projectile. */
    @Nullable
    private static Entity nearestInZone(ServerLevel level, AnomalyInstance instance, Vec3 flame) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        Entity living = nearest(level.getEntities((Entity) null, zone, e -> e instanceof LivingEntity && targetable(e)), flame);
        if (living != null) return living;
        return nearest(level.getEntities((Entity) null, zone, e -> e instanceof Projectile && targetable(e)), flame);
    }

    @Nullable
    private static Entity nearest(List<Entity> entities, Vec3 flame) {
        Entity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity entity : entities) {
            double d = entity.getBoundingBox().getCenter().distanceToSqr(flame);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = entity;
            }
        }
        return best;
    }

    private static boolean valid(Entity entity, Vec3 flame, double range) {
        return targetable(entity) && entity.getBoundingBox().getCenter().distanceToSqr(flame) <= range * range;
    }

    /** Living: alive, not a spectator, not a player in creative. Projectile: still flying (an
     *  arrow stuck in the ground doesn't keep setting it off). */
    private static boolean targetable(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        if (entity instanceof Projectile) return entity.getDeltaMovement().lengthSqr() > 1.0E-4;
        if (!(entity instanceof LivingEntity)) return false;
        return !(entity instanceof Player player && player.isCreative());
    }
}
