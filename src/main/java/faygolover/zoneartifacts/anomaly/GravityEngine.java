package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import faygolover.zoneartifacts.network.GorePacket;
import faygolover.zoneartifacts.network.GravityEventPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server side of the gravitational anomalies (see {@link Gravity} for the shared physics), called
 * from {@link AnomalyEngine} every tick.
 * <ul>
 *     <li><b>Plesh / Voronka / Karusel</b>: idle until a living being (not creative/spectator) or a
 *     flying projectile enters the zone, then a phase ({@code active}): Plesh/Voronka pull for
 *     {@code pullSeconds}, Karusel spins for {@code spinSeconds}. Then the release — Plesh throws
 *     everything up and away (a wall hit hurts, the fall afterwards hurts less), Voronka tears space
 *     (damage by distance, loot in the core destroyed, a shock wave scatters the rest, including what
 *     the dead drop), Karusel hurts whoever is still near its axis. Then the cooldown.</li>
 *     <li><b>Podushka</b>: always on; brakes, bounces, lets items settle and hang.</li>
 * </ul>
 * Players are moved by their own clients (the server can't move a player smoothly); the server
 * handles every mob, item and projectile, all damage and throws, and resets fall distance.
 * Voronka and Karusel kills burst into blood ({@link GorePacket}).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class GravityEngine {

    /** True while a Voronka tear / Karusel hit deals its damage: deaths then are torn apart. */
    private static boolean goreActive;

    /** Entities thrown by a Plesh: hitting a wall while still flying fast hurts. */
    private static final Map<UUID, Thrown> THROWN = new HashMap<>();
    /** Until when (game time) an entity's fall damage is reduced, and by how much — after a Plesh
     *  throw or a Podushka bounce. */
    private static final Map<UUID, SoftFall> SOFT_FALL = new HashMap<>();

    private record SoftFall(long until, double multiplier) {
    }

    private static final Map<UUID, Gravity.Bounce> BOUNCES = new HashMap<>();
    private static final Map<UUID, Integer> ITEM_BOUNCES = new HashMap<>();
    private static final Set<UUID> FROZEN_ITEMS = new HashSet<>();
    private static final Map<AnomalyInstance, Set<UUID>> INSIDE = new WeakHashMap<>();

    private static final class Thrown {
        final ResourceKey<Level> dimension;
        final float damage;
        final double throwSpeed;
        final long expires;
        final long started;
        Vec3 previous;
        double previousSpeed;

        Thrown(ResourceKey<Level> dimension, Vec3 at, float damage, double throwSpeed, long now) {
            this.dimension = dimension;
            this.previous = at;
            this.damage = damage;
            this.throwSpeed = throwSpeed;
            this.started = now;
            this.expires = now + 60;
            this.previousSpeed = throwSpeed;
        }
    }

    private GravityEngine() {
    }

    // ==== per anomaly =====================================================================

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        if (AnomalyTypeIds.PODUSHKA.equals(instance.typeId())) {
            tickPodushka(level, instance);
            return;
        }
        if (instance.active()) {
            tickPhase(level, instance);
            return;
        }
        if (instance.cooldownTicks() > 0) {
            instance.setCooldownTicks(instance.cooldownTicks() - 1);
            if (instance.cooldownTicks() == 0) AnomalySyncHandler.broadcastState(level, instance);
            return;
        }
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        if (!level.getEntities((Entity) null, zone, GravityEngine::triggers).isEmpty()) {
            start(level, instance);
        }
    }

    /** Living (not creative/spectator) or a projectile still in flight. Items never set it off. */
    private static boolean triggers(Entity entity) {
        if (!entity.isAlive() || AnomalyCombat.spectatorExempt(entity) || entity instanceof ArmorStand) return false;
        if (entity instanceof Projectile) return Gravity.flying(entity);
        return entity instanceof LivingEntity && !AnomalyCombat.creativeExempt(entity);
    }

    private static void start(ServerLevel level, AnomalyInstance instance) {
        instance.setActive(true);
        instance.setPulseTicks(0);
        instance.setPhaseSeed(level.random.nextInt());
        AnomalySyncHandler.broadcastState(level, instance);
        Vec3 c = Gravity.center(instance.pos(), instance.size());
        GravityEventPacket.send(level, instance.pos(), GravityEventPacket.START, instance.phaseSeed(), c);
        if (blowoutDelay(instance.typeId()) == 0) playBlowout(level, instance);
    }

    /** Ticks into the phase to start the blowout sound, so the burst in it falls on the release
     *  (right away if the phase is shorter than the sound's build-up). */
    private static int blowoutDelay(ResourceLocation type) {
        int burst = AnomalyTypeIds.KARUSEL.equals(type) ? Gravity.KARUSEL_BURST_TICKS
                : AnomalyTypeIds.VORONKA.equals(type) ? Gravity.VORONKA_BURST_TICKS : Gravity.PLESH_BURST_TICKS;
        return Math.max(0, phaseTicks(type) - burst);
    }

    private static void playBlowout(ServerLevel level, AnomalyInstance instance) {
        ResourceLocation type = instance.typeId();
        Vec3 c = Gravity.center(instance.pos(), instance.size());
        if (AnomalyTypeIds.KARUSEL.equals(type)) {
            AnomalyCombat.playSound(level, c, Gravity.KARUSEL_BLOWOUT_SOUND, 2.0f);
        } else if (AnomalyTypeIds.VORONKA.equals(type)) {
            AnomalyCombat.playSound(level, c, Gravity.VORONKA_BLOWOUT_SOUND, 2.5f);
        } else {
            AnomalyCombat.playSound(level, c, Gravity.PLESH_BLOWOUT_SOUND, 2.0f);
        }
    }

    private static int phaseTicks(ResourceLocation type) {
        double seconds = AnomalyTypeIds.KARUSEL.equals(type) ? ModCommonConfig.KARUSEL_SPIN_SECONDS.get()
                : AnomalyTypeIds.VORONKA.equals(type) ? ModCommonConfig.VORONKA_PULL_SECONDS.get()
                : ModCommonConfig.PLESH_PULL_SECONDS.get();
        return Gravity.phaseTicks(type, seconds);
    }

    private static void tickPhase(ServerLevel level, AnomalyInstance instance) {
        ResourceLocation type = instance.typeId();
        int t = instance.pulseTicks() + 1;
        instance.setPulseTicks(t);
        if (t >= phaseTicks(type)) {
            release(level, instance);
            return;
        }
        if (t == blowoutDelay(type)) playBlowout(level, instance);

        boolean karusel = AnomalyTypeIds.KARUSEL.equals(type);
        boolean plesh = AnomalyTypeIds.PLESH.equals(type);
        Vec3 c = Gravity.center(instance.pos(), instance.size());
        double r = Gravity.reach(instance.size());
        double force = instance.speed();

        for (Entity e : level.getEntities((Entity) null, new AABB(c, c).inflate(r + 1.0), Gravity::movable)) {
            boolean in = karusel ? Gravity.inCylinder(e, instance.pos(), instance.size()) : Gravity.inSphere(e, instance.pos(), instance.size());
            if (!in) continue;
            if (e instanceof LivingEntity living) living.resetFallDistance();
            if (e instanceof Player) continue; // its own client moves it

            Vec3 v;
            if (karusel) {
                v = Gravity.swirl(e.position(), e.getDeltaMovement(), c, r, force, e.onGround());
            } else {
                Gravity.Orbit orbit = plesh ? Gravity.Orbit.of(instance.phaseSeed(), e.getId()) : Gravity.Orbit.tight(instance.phaseSeed(), e.getId());
                v = Gravity.pull(e.getBoundingBox().getCenter(), e.getDeltaMovement(), c, force, Gravity.gravityOf(e), orbit, t);
            }
            e.setDeltaMovement(v);
            e.hasImpulse = true;
        }
    }

    private static void release(ServerLevel level, AnomalyInstance instance) {
        ResourceLocation type = instance.typeId();
        Vec3 c = Gravity.center(instance.pos(), instance.size());
        double r = Gravity.reach(instance.size());
        RandomSource random = level.random;

        if (AnomalyTypeIds.PLESH.equals(type)) {
            double speed = ModCommonConfig.PLESH_THROW_SPEED.get() * instance.speed();
            long now = level.getGameTime();
            for (Entity e : level.getEntities((Entity) null, new AABB(c, c).inflate(r + 1.0), Gravity::movable)) {
                if (!Gravity.inSphere(e, instance.pos(), instance.size())) continue;
                e.setDeltaMovement(Gravity.throwDirection(random).scale(speed));
                e.hurtMarked = true;
                e.hasImpulse = true;
                if (e instanceof LivingEntity) {
                    THROWN.put(e.getUUID(), new Thrown(level.dimension(), e.position(), instance.damage(), speed, now));
                    SOFT_FALL.put(e.getUUID(), new SoftFall(now + 200, ModCommonConfig.PLESH_FALL_DAMAGE_MULTIPLIER.get()));
                }
            }
        } else if (AnomalyTypeIds.VORONKA.equals(type)) {
            double core = ModCommonConfig.VORONKA_CORE_RADIUS.get();
            destroyItems(level, c, core, null);
            goreActive = true;
            try {
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 1.0), GravityEngine::hurtable)) {
                    double d = victim.getBoundingBox().getCenter().distanceTo(c);
                    if (d > r) continue;
                    victim.invulnerableTime = 0;
                    AnomalyCombat.hurt(level, victim, Gravity.GRAVITY_DAMAGE_TYPE, (float) (instance.damage() * Gravity.falloff(d, r, 0.3)), c);
                }
            } finally {
                goreActive = false;
            }
            shockwave(level, c, r * 1.2, 0.7);
        } else {
            double core = ModCommonConfig.KARUSEL_CORE_RADIUS.get();
            destroyItems(level, c, core, instance);
            goreActive = true;
            try {
                // Everyone in the whirl is hurt: fully at the axis, a quarter at its edge.
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 1.0), GravityEngine::hurtable)) {
                    if (!Gravity.inCylinder(victim, instance.pos(), instance.size())) continue;
                    double d = Gravity.horizontalDistance(victim, c);
                    victim.invulnerableTime = 0;
                    AnomalyCombat.hurt(level, victim, Gravity.GRAVITY_DAMAGE_TYPE, (float) (instance.damage() * Gravity.falloff(d, r, 0.25)), c);
                }
            } finally {
                goreActive = false;
            }
            double bottom = AnomalyGeometry.zoneAabb(instance).minY;
            horizontalShockwave(level, c, bottom - 0.5, bottom + r + 0.5, r * 1.3, 0.55);
        }

        GravityEventPacket.send(level, instance.pos(), GravityEventPacket.RELEASE, instance.phaseSeed(), c);
        instance.setActive(false);
        instance.setPulseTicks(0);
        instance.setCooldownTicks(AnomalyDefaults.ticks(instance.cooldownSeconds()));
        AnomalySyncHandler.broadcastState(level, instance);
    }

    private static boolean hurtable(LivingEntity entity) {
        if (!entity.isAlive() || AnomalyCombat.spectatorExempt(entity)) return false;
        return !AnomalyCombat.creativeExempt(entity);
    }

    /** Items within {@code radius} of the center vanish in a puff (Karusel: measured to its axis,
     *  inside its cylinder). */
    private static void destroyItems(ServerLevel level, Vec3 c, double radius, AnomalyInstance karusel) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(c, c).inflate(radius + (karusel != null ? Gravity.reach(karusel.size()) : 1.0)));
        for (ItemEntity item : items) {
            boolean inCore = karusel != null
                    ? Gravity.inCylinder(item, karusel.pos(), karusel.size()) && Gravity.horizontalDistance(item, c) <= radius
                    : item.getBoundingBox().getCenter().distanceTo(c) <= radius;
            if (!inCore) continue;
            Vec3 p = item.position();
            level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.2, p.z, 4, 0.1, 0.1, 0.1, 0.02);
            item.discard();
        }
    }

    /** Pushes everything movable away from {@code c}: stronger closer in, always a little upwards. */
    private static void shockwave(ServerLevel level, Vec3 c, double radius, double strength) {
        for (Entity e : level.getEntities((Entity) null, new AABB(c, c).inflate(radius), Gravity::movable)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(c);
            double d = to.length();
            if (d > radius) continue;
            Vec3 dir = d < 1.0E-3 ? new Vec3(0, 1, 0) : to.scale(1.0 / d);
            double k = strength * (0.3 + 0.7 * (1.0 - d / radius));
            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * k, Math.max(0.15, dir.y * k + 0.25 * k), dir.z * k));
            e.hurtMarked = true;
            e.hasImpulse = true;
        }
    }

    /** Karusel's wave of compressed air: straight out from its axis, flat, only a slight lift. */
    private static void horizontalShockwave(ServerLevel level, Vec3 c, double minY, double maxY, double radius, double strength) {
        AABB box = new AABB(c.x - radius, minY, c.z - radius, c.x + radius, maxY, c.z + radius);
        for (Entity e : level.getEntities((Entity) null, box, Gravity::movable)) {
            double dx = e.getX() - c.x;
            double dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius) continue;
            double angle = d < 1.0E-3 ? level.random.nextDouble() * Math.PI * 2.0 : Math.atan2(dz, dx);
            double k = strength * (0.35 + 0.65 * (1.0 - d / radius));
            e.setDeltaMovement(e.getDeltaMovement().add(Math.cos(angle) * k, 0.1 * k, Math.sin(angle) * k));
            e.hurtMarked = true;
            e.hasImpulse = true;
        }
    }

    // ==== Podushka =========================================================================

    private static void tickPodushka(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        double top = zone.maxY;
        double height = ModCommonConfig.PODUSHKA_HEIGHT.get() * instance.speed();
        Set<UUID> before = INSIDE.computeIfAbsent(instance, k -> new HashSet<>());
        Set<UUID> now = new HashSet<>();

        for (Entity e : level.getEntities((Entity) null, zone, Gravity::movable)) {
            UUID id = e.getUUID();
            now.add(id);
            if (e instanceof LivingEntity living) {
                living.resetFallDistance();
                if (!before.contains(id)) {
                    AnomalyCombat.playSound(level, e.position(), Gravity.PODUSHKA_BOUNCE_SOUND, 0.8f);
                    GravityEventPacket.send(level, instance.pos(), GravityEventPacket.BOUNCE, 0, e.position());
                }
            }
            if (e instanceof Player) continue; // its own client bounces it

            if (e instanceof ItemEntity item) {
                tickItem(level, item, top, height);
                continue;
            }
            Gravity.Bounce bounce = BOUNCES.computeIfAbsent(id, k -> Gravity.startBounce(e.getDeltaMovement(), level.random));
            e.setDeltaMovement(Gravity.bounce(e.getDeltaMovement(), e.getY(), top, height, Gravity.gravityOf(e), bounce));
            e.hasImpulse = true;
        }

        // Whatever left the cushion starts fresh next time (and a hanging item falls again). An item
        // just hopping above it keeps its count, so its bounces keep getting lower.
        for (UUID id : before) {
            if (now.contains(id)) continue;
            BOUNCES.remove(id);
            Entity left = level.getEntity(id);
            // Bounced out (not just walked off): the landing hurts less, as after a Plesh throw.
            if (left instanceof LivingEntity living && living.isAlive() && !living.onGround()) {
                SOFT_FALL.put(id, new SoftFall(level.getGameTime() + 200, ModCommonConfig.PODUSHKA_FALL_DAMAGE_MULTIPLIER.get()));
            }
            boolean overCushion = left instanceof ItemEntity && left.isAlive()
                    && left.getX() >= zone.minX && left.getX() <= zone.maxX
                    && left.getZ() >= zone.minZ && left.getZ() <= zone.maxZ && left.getY() >= zone.minY;
            if (!overCushion) ITEM_BOUNCES.remove(id);
            if (FROZEN_ITEMS.remove(id) && left instanceof ItemEntity item) unfreeze(item);
        }
        INSIDE.put(instance, now);
    }

    /** Items bounce lower each time (x0.7) and finally stop and hang in the cushion. */
    private static void tickItem(ServerLevel level, ItemEntity item, double top, double height) {
        UUID id = item.getUUID();
        if (FROZEN_ITEMS.contains(id)) {
            item.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Gravity.Bounce bounce = BOUNCES.get(id);
        if (bounce == null) {
            bounce = Gravity.startBounce(item.getDeltaMovement(), level.random);
            BOUNCES.put(id, bounce);
            ITEM_BOUNCES.merge(id, 1, Integer::sum);
        }
        int count = ITEM_BOUNCES.getOrDefault(id, 1);
        double h = height * Math.pow(0.7, count - 1);
        Vec3 v = item.getDeltaMovement();
        if (h < 0.35) {
            if (Math.abs(v.y) < 0.05) {
                FROZEN_ITEMS.add(id);
                item.setNoGravity(true);
                item.getPersistentData().putBoolean(HANG_TAG, true);
                item.setDeltaMovement(Vec3.ZERO);
            } else {
                // Slow to a stop in mid-air (gravity cancelled), then hang there.
                item.setDeltaMovement(v.x * 0.8, v.y * 0.7 + 0.01 + Gravity.gravityOf(item), v.z * 0.8);
            }
            return;
        }
        item.setDeltaMovement(Gravity.bounce(v, item.getY(), top, h, Gravity.gravityOf(item), bounce));
        item.hasImpulse = true;
    }

    private static final String HANG_TAG = ZoneArtifacts.MODID + "_hang";

    private static void unfreeze(ItemEntity item) {
        item.setNoGravity(false);
        item.getPersistentData().remove(HANG_TAG);
    }

    /** A Podushka was removed: whatever hung in it falls. */
    public static void forget(ServerLevel level, AnomalyInstance instance) {
        Set<UUID> inside = INSIDE.remove(instance);
        if (inside == null) return;
        for (UUID id : inside) {
            BOUNCES.remove(id);
            ITEM_BOUNCES.remove(id);
            if (FROZEN_ITEMS.remove(id) && level.getEntity(id) instanceof ItemEntity item) unfreeze(item);
        }
    }

    /** "No gravity" is saved with the item but our list isn't: after a reload a hanging item falls. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item
                && item.getPersistentData().getBoolean(HANG_TAG)) {
            unfreeze(item);
        }
    }

    // ==== throws, falls, deaths ===============================================================

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (THROWN.isEmpty() && SOFT_FALL.isEmpty()) return;
        long now = level.getGameTime();

        for (Iterator<Map.Entry<UUID, Thrown>> it = THROWN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Thrown> entry = it.next();
            Thrown t = entry.getValue();
            if (!t.dimension.equals(level.dimension())) continue;
            if (now > t.expires || !(level.getEntity(entry.getKey()) instanceof LivingEntity e) || !e.isAlive()) {
                it.remove();
                continue;
            }
            Vec3 at = e.position();
            double dx = at.x - t.previous.x;
            double dz = at.z - t.previous.z;
            double speed = Math.sqrt(dx * dx + dz * dz);
            // A player's collisions aren't known on the server: a sudden stop in mid-flight counts as the wall.
            boolean hitWall = e.horizontalCollision
                    || (e instanceof Player && now - t.started > 1 && speed < t.previousSpeed * 0.35);
            if (hitWall && t.previousSpeed > 0.35) {
                float damage = (float) (t.damage * Mth.clamp(t.previousSpeed / t.throwSpeed, 0.2, 1.0));
                AnomalyCombat.hurt(level, e, Gravity.IMPACT_DAMAGE_TYPE, damage);
                it.remove();
                continue;
            }
            if (e.onGround() && now - t.started > 5) {
                it.remove();
                continue;
            }
            t.previousSpeed = speed;
            t.previous = at;
        }
        SOFT_FALL.values().removeIf(soft -> soft.until() < now - 1200);
    }

    /** Fall damage of {@code entity} is multiplied by {@code multiplier} until {@code until} (its
     *  next landing), e.g. after leaving a Lift. */
    public static void softenFall(LivingEntity entity, long until, double multiplier) {
        SOFT_FALL.put(entity.getUUID(), new SoftFall(until, multiplier));
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        SoftFall soft = SOFT_FALL.remove(entity.getUUID());
        if (soft != null && entity.level().getGameTime() <= soft.until()) {
            event.setDamageMultiplier(event.getDamageMultiplier() * (float) soft.multiplier());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!goreActive) return;
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        GorePacket.send(entity);
        AnomalyCombat.playSound(level, entity.getBoundingBox().getCenter(), Gravity.GORE_SOUND, 1.2f);
    }
}
