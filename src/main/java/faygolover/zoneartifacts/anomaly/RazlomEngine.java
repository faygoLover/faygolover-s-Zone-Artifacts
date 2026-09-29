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
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Server side of the Razlom and the Cold Razlom (soul fire: cold damage and freezing instead of
 * fire damage and burning, frozen spots instead of fires), called from {@link AnomalyEngine} every tick.
 * <ol>
 *     <li><b>Idle</b>: waits for a living being in its zone (not a spectator, not a player in
 *     creative) or, failing that, a flying projectile (a snowball, an arrow...) — like the Electra,
 *     so it can be set off from a distance and discharged.</li>
 *     <li><b>Jet</b> ({@code active}), always the full {@code razlom.jetSeconds}: the jet runs along
 *     an arc from the flame to its <i>aim point</i>. The aim point chases the target at no more
 *     than {@code aimSpeed} blocks per tick, smoothly (so a sprinting player can outrun it, and a
 *     miss really misses — the fire lands wherever the arc does). With no target (it ran out of
 *     range, died, the projectile flew off) the jet keeps going: the aim point drifts on with its
 *     momentum, wanders a little and sinks to the ground, until a new target turns up or time is up.
 *     Every {@code hitIntervalTicks} whoever the arc passes through is burned; now and then
 *     ({@code blockIgniteChance}) a fire starts next to a victim or where the jet hits a block.</li>
 *     <li><b>Cooldown</b> ({@link AnomalyInstance#cooldownSeconds()}): the flame is out, then it waits again.</li>
 * </ol>
 * The aim point is sent to nearby clients every tick ({@link RazlomJetPacket}), so everyone sees the
 * jet exactly where it burns.
 */
public final class RazlomEngine {

    /** How close to the arc an entity's box must be to get burned. */
    private static final double JET_THICKNESS = 0.3;

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
        // First hit as the jet arrives (it shoots out over JET_GROW_TICKS), then every hitIntervalTicks.
        instance.setBlockTicks(Math.max(0, ModCommonConfig.RAZLOM_HIT_INTERVAL_TICKS.get() - Razlom.JET_GROW_TICKS));
        instance.setJetTargetId(target.getId());
        instance.setJetAim(target.getBoundingBox().getCenter());
        instance.setJetVelocity(Vec3.ZERO);
        instance.setJetWander(level.random.nextDouble() * Math.PI * 2.0);
        AnomalySyncHandler.broadcastState(level, instance);
        RazlomJetPacket.send(level, instance.pos(), target.getId(), jetTicks, instance.jetAim());
    }

    private static void tickJet(ServerLevel level, AnomalyInstance instance) {
        Vec3 flame = Razlom.flamePos(level, instance.pos(), instance.size());
        double range = Razlom.jetRange(instance.size(), ModCommonConfig.RAZLOM_JET_RANGE.get());
        int remaining = instance.pulseTicks() - 1;
        instance.setPulseTicks(remaining);
        if (remaining <= 0) {
            endJet(level, instance);
            return;
        }

        // Keep the target while it's valid and in reach, else take whoever is in the zone now.
        Entity current = level.getEntity(instance.jetTargetId());
        Entity target = current != null && valid(current, flame, range) ? current : nearestInZone(level, instance, flame);
        instance.setJetTargetId(target != null ? target.getId() : -1);

        moveAim(level, instance, flame, range, target);

        int hitTicks = instance.blockTicks() + 1;
        if (hitTicks >= ModCommonConfig.RAZLOM_HIT_INTERVAL_TICKS.get()) {
            hitTicks = 0;
            burn(level, instance, flame);
        }
        instance.setBlockTicks(hitTicks);

        RazlomJetPacket.send(level, instance.pos(), instance.jetTargetId(), remaining, instance.jetAim());
    }

    /**
     * Steers the aim point: after the target at up to {@code aimSpeed}, smoothed so it swings
     * rather than snaps; without one, it coasts on, wanders and falls, then slides along the ground.
     * It never strays further than {@code range} from the flame.
     */
    private static void moveAim(ServerLevel level, AnomalyInstance instance, Vec3 flame, double range, @Nullable Entity target) {
        Vec3 aim = instance.jetAim();
        Vec3 velocity = instance.jetVelocity();
        double maxSpeed = ModCommonConfig.RAZLOM_AIM_SPEED.get();

        if (target != null) {
            Vec3 want = target.getBoundingBox().getCenter().subtract(aim);
            double distance = want.length();
            Vec3 wantVelocity = distance < 1.0E-4 ? Vec3.ZERO : want.scale(Math.min(maxSpeed, distance * 0.5) / distance);
            velocity = velocity.scale(0.7).add(wantVelocity.scale(0.3));
        } else {
            // Coast: horizontal momentum fades slowly, a gently turning heading nudges it along,
            // and the stream sags towards the ground.
            double wander = instance.jetWander() + level.random.nextGaussian() * 0.12;
            instance.setJetWander(wander);
            Vec3 nudge = new Vec3(Math.cos(wander), 0.0, Math.sin(wander)).scale(0.02);
            velocity = new Vec3(velocity.x * 0.96, Math.max(-0.3, velocity.y - 0.015), velocity.z * 0.96).add(nudge);
            double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (horizontal > maxSpeed) velocity = new Vec3(velocity.x * maxSpeed / horizontal, velocity.y, velocity.z * maxSpeed / horizontal);
        }

        aim = aim.add(velocity);

        // Stay within reach of the flame: pull back and bounce off the edge.
        Vec3 offset = aim.subtract(flame);
        double dist = offset.length();
        if (dist > range && dist > 1.0E-4) {
            aim = flame.add(offset.scale(range / dist));
            Vec3 outward = offset.scale(1.0 / dist);
            double out = velocity.dot(outward);
            if (out > 0) velocity = velocity.subtract(outward.scale(out * 1.5));
        }

        // Never below the ground: once down, it slides along it.
        Double ground = Razlom.groundY(level, aim.x, aim.z, aim.y + 1.5, aim.y - 8.0);
        if (ground != null && aim.y < ground + 0.1) {
            aim = new Vec3(aim.x, ground + 0.1, aim.z);
            if (velocity.y < 0) velocity = new Vec3(velocity.x, 0.0, velocity.z);
        }

        instance.setJetAim(aim);
        instance.setJetVelocity(velocity);
    }

    /** Burns whoever the arc passes through; with a small chance, starts a fire where it lands. */
    private static void burn(ServerLevel level, AnomalyInstance instance, Vec3 flame) {
        // The Cold Razlom's soul fire freezes where this one burns.
        boolean cold = AnomalyTypeIds.COLD_RAZLOM.equals(instance.typeId());
        Razlom.Arc arc = Razlom.arc(level, flame, instance.jetAim(), 1.0);
        List<Vec3> points = arc.points();
        double blockChance = ModCommonConfig.RAZLOM_BLOCK_IGNITE_CHANCE.get();

        AABB bounds = new AABB(flame, flame);
        for (Vec3 p : points) bounds = bounds.minmax(new AABB(p, p));
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, bounds.inflate(1.0), RazlomEngine::targetable);

        boolean hitSomeone = false;
        for (LivingEntity victim : near) {
            AABB box = victim.getBoundingBox().inflate(JET_THICKNESS);
            if (!touches(box, points)) continue;
            hitSomeone = true;
            // Hits land every few ticks on purpose: don't let vanilla's half-second of
            // invulnerability swallow them.
            victim.invulnerableTime = 0;
            if (cold) {
                AnomalyCombat.hurt(level, victim, Thermal.COLD_DAMAGE_TYPE, instance.damage());
                ColdEffects.freeze(victim, ModCommonConfig.COLD_RAZLOM_FREEZE_SECONDS.get());
            } else {
                AnomalyCombat.hurt(level, victim, Thermal.HEAT_DAMAGE_TYPE, instance.damage());
                int ignite = ModCommonConfig.RAZLOM_IGNITE_SECONDS.get();
                if (ignite > 0 && !victim.fireImmune()) victim.setSecondsOnFire(ignite);
            }
            if (level.random.nextDouble() < blockChance) igniteNear(level, victim.blockPosition(), cold);
        }

        // The stream splashing onto a block (a miss, the ground, or something in the way).
        if (!hitSomeone && arc.hit() != null && level.random.nextDouble() < blockChance) {
            igniteSpot(level, arc.hit().getBlockPos().relative(arc.hit().getDirection()), cold);
        }
    }

    private static boolean touches(AABB box, List<Vec3> points) {
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i);
            if (box.contains(a) || box.clip(a, points.get(i + 1)).isPresent()) return true;
        }
        return box.contains(points.get(points.size() - 1));
    }

    private static void endJet(ServerLevel level, AnomalyInstance instance) {
        instance.setActive(false);
        instance.setPulseTicks(0);
        instance.setJetTargetId(-1);
        instance.setJetVelocity(Vec3.ZERO);
        instance.setCooldownTicks(AnomalyDefaults.ticks(instance.cooldownSeconds()));
        AnomalySyncHandler.broadcastState(level, instance);
        RazlomJetPacket.send(level, instance.pos(), -1, 0, instance.jetAim());
    }

    /** A fire on a free spot right around {@code feet} (a few tries). */
    private static void igniteNear(ServerLevel level, BlockPos feet, boolean cold) {
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos pos = feet.offset(level.random.nextInt(3) - 1, 0, level.random.nextInt(3) - 1);
            if (igniteSpot(level, pos, cold)) return;
        }
    }

    /** Fire on a free spot — or, for the Cold Razlom, the spot freezes ({@link ColdEffects#chill}). */
    private static boolean igniteSpot(ServerLevel level, BlockPos pos, boolean cold) {
        if (cold) return ColdEffects.chill(level, pos);
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
