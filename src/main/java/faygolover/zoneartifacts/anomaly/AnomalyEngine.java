package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

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

    private static AABB zoneAabb(AnomalyInstance instance, AnomalyType type) {
        int size = type.shape().sizeForLevel(instance.level());
        BlockPos pos = instance.pos();
        double half = size / 2.0;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        return new AABB(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
    }

    // ---- ambient (always-on) visual/sound ---------------------------------

    private static void tickAmbient(ServerLevel level, AnomalyInstance instance, AnomalyType type) {
        AnomalyVisualSound ambient = type.ambient();
        if (ambient == null) return;

        AABB aabb = zoneAabb(instance, type);

        if (ambient.particle() != null && ambient.intervalTicks() > 0) {
            int t = instance.ambientParticleTicker() - 1;
            if (t <= 0) {
                spawnParticles(level, aabb, ambient);
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

    private static void spawnParticles(ServerLevel level, AABB aabb, AnomalyVisualSound visual) {
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

        AABB aabb = zoneAabb(instance, type);
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
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, type.effect().damageType());
        DamageSource source = level.damageSources().source(key);
        float amount = type.effect().damageForLevel(instance.level());
        target.hurt(source, amount);
    }

    private static void playTriggerEffect(ServerLevel level, AABB aabb, AnomalyType type) {
        AnomalyVisualSound trigger = type.triggerEffect();
        if (trigger == null) return;
        if (trigger.particle() != null) {
            spawnParticles(level, aabb, trigger);
        }
        if (trigger.sound() != null) {
            playSound(level, aabb, trigger.sound(), trigger.soundVolume(), trigger.soundPitch());
        }
    }
}
