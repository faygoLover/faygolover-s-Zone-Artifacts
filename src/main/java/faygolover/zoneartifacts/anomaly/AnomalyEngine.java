package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side tick logic for placed anomalies: ambient particles/sound, and — for
 * {@link AnomalyTrigger.TriggerType#BURST} anomalies like Electra — detecting entities inside
 * the zone, applying damage, playing the one-shot trigger effect, and running the cooldown.
 * <p>
 * Anomalies are re-checked every server tick while off cooldown, which is deliberately simple:
 * fine for the handful of anomalies a set of GMs will place by hand. If this ever needs to
 * scale to hundreds of anomalies, that's the place to optimize (e.g. only scanning near loaded
 * players, or a spatial index) — not a stage-1 concern.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class AnomalyEngine {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        AnomalySavedData data = AnomalySavedData.get(serverLevel);
        for (AnomalyInstance instance : List.copyOf(data.instances())) {
            tickInstance(serverLevel, instance);
        }
    }

    private static void tickInstance(ServerLevel level, AnomalyInstance instance) {
        AnomalyType type = AnomalyTypeManager.get(instance.typeId());
        if (type == null) {
            // Datapack removed/renamed this type; leave the placed instance alone (it'll pick
            // back up automatically if the type reappears on a later /reload).
            return;
        }

        tickAmbient(level, instance, type);

        if (type.trigger().type() == AnomalyTrigger.TriggerType.BURST) {
            tickBurst(level, instance, type);
        }
        // PASSIVE_FIELD / PHASED: not implemented yet, see AnomalyTrigger's javadoc.
    }

    // ---- ambient (always-on) visual/sound ---------------------------------

    private static void tickAmbient(ServerLevel level, AnomalyInstance instance, AnomalyType type) {
        AnomalyVisualSound ambient = type.ambient();
        if (ambient == null) return;

        AABB aabb = AnomalyGeometry.zoneAabb(instance, type);

        if (ambient.particle() != null && ambient.intervalTicks() > 0) {
            int t = instance.ambientParticleTicker() - 1;
            if (t <= 0) {
                spawnParticlesOnSurfaces(level, aabb, ambient);
                t = ambient.intervalTicks();
            }
            instance.setAmbientParticleTicker(t);
        }

        if (ambient.sound() != null && ambient.soundIntervalTicks() > 0) {
            int t = instance.ambientSoundTicker() - 1;
            if (t <= 0) {
                playSound(level, aabb, ambient.sound(), ambient.soundVolume(), ambient.soundPitch());
                t = ambient.soundIntervalTicks();
            }
            instance.setAmbientSoundTicker(t);
        }
    }

    /**
     * Ambient sparks should look like they're jumping between surfaces the zone touches, not
     * floating randomly in mid-air. This scans the block positions the zone's AABB overlaps
     * (plus their immediate neighbors) for "solid face next to non-solid space" pairs and spawns
     * particles on those faces; if the zone doesn't touch any solid surface at all (floating in
     * open air), it simply spawns nothing that tick rather than falling back to mid-air points.
     */
    private static void spawnParticlesOnSurfaces(ServerLevel level, AABB aabb, AnomalyVisualSound visual) {
        if (!(ForgeRegistries.PARTICLE_TYPES.getValue(visual.particle()) instanceof SimpleParticleType particleType)) {
            return;
        }

        List<Vec3Point> surfacePoints = findSurfacePoints(level, aabb);
        if (surfacePoints.isEmpty()) return;

        for (int i = 0; i < visual.particleCount(); i++) {
            Vec3Point p = surfacePoints.get(level.random.nextInt(surfacePoints.size()));
            level.sendParticles(particleType, p.x(), p.y(), p.z(), 1, 0, 0, 0, 0.0);
        }
    }

    private record Vec3Point(double x, double y, double z) {
    }

    private static List<Vec3Point> findSurfacePoints(ServerLevel level, AABB aabb) {
        List<Vec3Point> points = new ArrayList<>();
        BlockPos min = BlockPos.containing(aabb.minX, aabb.minY, aabb.minZ);
        BlockPos max = BlockPos.containing(aabb.maxX, aabb.maxY, aabb.maxZ);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (isSolid(level, pos)) continue; // looking for empty cells with a solid neighbor
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (isSolid(level, neighbor)) {
                    points.add(faceMidpoint(pos.immutable(), dir, level));
                }
            }
        }
        return points;
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static Vec3Point faceMidpoint(BlockPos emptyPos, Direction dir, ServerLevel level) {
        double baseX = emptyPos.getX() + 0.5 + dir.getStepX() * 0.5;
        double baseY = emptyPos.getY() + 0.5 + dir.getStepY() * 0.5;
        double baseZ = emptyPos.getZ() + 0.5 + dir.getStepZ() * 0.5;

        double jitter = 0.35;
        Direction.Axis axis = dir.getAxis();
        double jx = axis == Direction.Axis.X ? 0 : (level.random.nextDouble() - 0.5) * jitter;
        double jy = axis == Direction.Axis.Y ? 0 : (level.random.nextDouble() - 0.5) * jitter;
        double jz = axis == Direction.Axis.Z ? 0 : (level.random.nextDouble() - 0.5) * jitter;

        return new Vec3Point(baseX + jx, baseY + jy, baseZ + jz);
    }

    /** Used for the one-shot trigger burst, where a diffuse mid-air flash reads fine — unlike the
     *  ambient hum, it isn't meant to look surface-anchored. */
    private static void spawnParticlesInVolume(ServerLevel level, AABB aabb, AnomalyVisualSound visual) {
        if (!(ForgeRegistries.PARTICLE_TYPES.getValue(visual.particle()) instanceof SimpleParticleType particleType)) {
            return;
        }
        for (int i = 0; i < visual.particleCount(); i++) {
            double x = lerp(level.random.nextDouble(), aabb.minX, aabb.maxX);
            double y = lerp(level.random.nextDouble(), aabb.minY, aabb.maxY);
            double z = lerp(level.random.nextDouble(), aabb.minZ, aabb.maxZ);
            level.sendParticles(particleType, x, y, z, 1, 0, 0, 0, 0.0);
        }
    }

    private static void playSound(ServerLevel level, AABB aabb, ResourceLocation soundId, float volume, float pitch) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound == null) return;
        double x = (aabb.minX + aabb.maxX) / 2.0;
        double y = (aabb.minY + aabb.maxY) / 2.0;
        double z = (aabb.minZ + aabb.maxZ) / 2.0;
        level.playSound(null, x, y, z, sound, SoundSource.AMBIENT, volume, pitch);
    }

    private static double lerp(double t, double min, double max) {
        return min + (max - min) * t;
    }

    // ---- burst trigger -----------------------------------------------------

    private static void tickBurst(ServerLevel level, AnomalyInstance instance, AnomalyType type) {
        int cooldown = instance.cooldownTicks() - 1;
        if (cooldown > 0) {
            instance.setCooldownTicks(cooldown);
            return;
        }

        AABB aabb = AnomalyGeometry.zoneAabb(instance, type);
        AnomalyDetect detect = type.detect();
        List<Entity> hits = level.getEntities((Entity) null, aabb, e -> matches(e, detect));
        if (hits.isEmpty()) return;

        boolean firedOnSomething = false;
        for (Entity entity : hits) {
            if (entity instanceof LivingEntity living) {
                applyDamage(level, instance, type, living);
                firedOnSomething = true;
            } else if (entity instanceof Projectile) {
                // Thrown items (snowballs, eggs, ...) just trip the anomaly, they take no damage.
                firedOnSomething = true;
            }
        }

        if (firedOnSomething) {
            playTriggerEffect(level, aabb, type);
            instance.setCooldownTicks(type.trigger().cooldownTicks());
        }
    }

    private static boolean matches(Entity entity, AnomalyDetect detect) {
        if (entity instanceof Player) return detect.players();
        if (entity instanceof Projectile) return detect.thrownProjectiles();
        if (entity instanceof LivingEntity) return detect.mobs();
        return false;
    }

    private static void applyDamage(ServerLevel level, AnomalyInstance instance, AnomalyType type, LivingEntity target) {
        // DamageSources' own source(ResourceKey) helper is private (used only for vanilla's
        // built-in damage types), so a custom damage type has to be wrapped by hand: look up its
        // Holder in the damage-type registry and build the DamageSource directly from that.
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, type.effect().damageType());
        Optional<Holder.Reference<DamageType>> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key);
        if (holder.isEmpty()) {
            // damage_type json for this id is missing/misspelled; skip rather than crash.
            return;
        }
        DamageSource source = new DamageSource(holder.get());
        float amount = type.effect().damageForLevel(instance.level());
        target.hurt(source, amount);
    }

    private static void playTriggerEffect(ServerLevel level, AABB aabb, AnomalyType type) {
        AnomalyVisualSound trigger = type.triggerEffect();
        if (trigger == null) return;
        if (trigger.particle() != null) {
            spawnParticlesInVolume(level, aabb, trigger);
        }
        if (trigger.sound() != null) {
            playSound(level, aabb, trigger.sound(), trigger.soundVolume(), trigger.soundPitch());
        }
    }
}
