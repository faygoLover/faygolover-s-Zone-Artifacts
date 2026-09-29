package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side tick logic for placed anomalies — for {@link AnomalyTrigger.TriggerType#BURST}
 * anomalies like Electra: detecting entities inside the zone, applying damage, playing the
 * one-shot trigger effect, and running the cooldown.
 * <p>
 * Everything "ambient" (the idle sound loop, the lightning-arc visual) is purely a client-side
 * concern now — see {@code AnomalyAmbientSoundHandler} / {@code AnomalyArcRenderer} — driven by
 * the {@code onCooldown} flag this class keeps synced via {@link AnomalySyncHandler#broadcast}.
 * The server itself has nothing left to tick for ambient beyond the cooldown counter below.
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

        if (type.trigger().type() == AnomalyTrigger.TriggerType.BURST) {
            tickBurst(level, instance, type);
        }
        // PASSIVE_FIELD / PHASED: not implemented yet, see AnomalyTrigger's javadoc.
    }

    /** Used for the one-shot trigger burst, where a diffuse mid-air flash reads fine. */
    private static void spawnParticlesInVolume(ServerLevel level, AABB aabb, ResourceLocation particleId, int count) {
        if (!(ForgeRegistries.PARTICLE_TYPES.getValue(particleId) instanceof SimpleParticleType particleType)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double x = lerp(level.random.nextDouble(), aabb.minX, aabb.maxX);
            double y = lerp(level.random.nextDouble(), aabb.minY, aabb.maxY);
            double z = lerp(level.random.nextDouble(), aabb.minZ, aabb.maxZ);
            level.sendParticles(particleType, x, y, z, 1, 0, 0, 0, 0.0);
        }
    }

    private static void playSound(ServerLevel level, AABB aabb, ResourceLocation soundId, float volume, float pitch) {
        double x = (aabb.minX + aabb.maxX) / 2.0;
        double y = (aabb.minY + aabb.maxY) / 2.0;
        double z = (aabb.minZ + aabb.maxZ) / 2.0;
        playSoundAt(level, new Vec3(x, y, z), soundId, volume, pitch);
    }

    /** Same as {@link #playSound} but centered on an arbitrary point rather than a zone's
     *  middle — used for Electra's per-target "hit" sound, which should come from the shocked
     *  entity rather than the anomaly itself. */
    private static void playSoundAt(ServerLevel level, Vec3 pos, ResourceLocation soundId, float volume, float pitch) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound == null) return;
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.AMBIENT, volume, pitch);
    }

    private static double lerp(double t, double min, double max) {
        return min + (max - min) * t;
    }

    // ---- burst trigger -----------------------------------------------------

    private static void tickBurst(ServerLevel level, AnomalyInstance instance, AnomalyType type) {
        boolean wasOnCooldown = instance.cooldownTicks() > 0;

        // Always write the decremented value back, even once it reaches 0 — leaving the field
        // untouched once "cooldown > 0" stops being true would freeze it on its last nonzero value
        // forever, since nothing else ever zeroes it out.
        int cooldown = Math.max(0, instance.cooldownTicks() - 1);
        instance.setCooldownTicks(cooldown);

        if (wasOnCooldown && cooldown == 0) {
            // Cooldown just fully ended: the client-side idle-loop and arc-visual handlers decide
            // whether to run based on this synced "on cooldown" flag, so it needs telling the
            // instant this flips or they'd stay dark even though the server side has moved on.
            AnomalySyncHandler.broadcast(level);
        }

        if (cooldown > 0) {
            return;
        }

        AABB aabb = AnomalyGeometry.zoneAabb(instance, type);
        AnomalyDetect detect = type.detect();
        List<Entity> hits = level.getEntities((Entity) null, aabb, e -> matches(e, detect));
        if (hits.isEmpty()) return;

        List<LivingEntity> hitLiving = new ArrayList<>();
        boolean projectileTripped = false;
        for (Entity entity : hits) {
            if (entity instanceof LivingEntity living) {
                if (applyDamage(level, instance, type, living)) {
                    hitLiving.add(living);
                }
            } else if (entity instanceof Projectile) {
                // Thrown items (snowballs, eggs, ...) just trip the anomaly, they take no damage.
                projectileTripped = true;
            }
        }

        if (!hitLiving.isEmpty() || projectileTripped) {
            playTriggerEffect(level, aabb, type, hitLiving, projectileTripped);
            instance.setCooldownTicks(type.trigger().cooldownTicks());
            // Cooldown just started: tell the client right away so it can stop the idle loop and
            // arcs the moment the anomaly fires, rather than waiting for some unrelated future sync.
            AnomalySyncHandler.broadcast(level);
        }
    }

    private static boolean matches(Entity entity, AnomalyDetect detect) {
        if (entity instanceof Player) return detect.players();
        if (entity instanceof Projectile) return detect.thrownProjectiles();
        if (entity instanceof LivingEntity) return detect.mobs();
        return false;
    }

    /** @return true if a damage source was actually resolved and applied (i.e. this hit "counts"
     *  for the living-vs-projectile trigger-sound split below). */
    private static boolean applyDamage(ServerLevel level, AnomalyInstance instance, AnomalyType type, LivingEntity target) {
        // DamageSources' own source(ResourceKey) helper is private (used only for vanilla's
        // built-in damage types), so a custom damage type has to be wrapped by hand: look up its
        // Holder in the damage-type registry and build the DamageSource directly from that.
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, type.effect().damageType());
        Optional<Holder.Reference<DamageType>> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key);
        if (holder.isEmpty()) {
            // damage_type json for this id is missing/misspelled; skip rather than crash.
            return false;
        }
        DamageSource source = new DamageSource(holder.get());
        float amount = type.effect().damageForLevel(instance.level());
        target.hurt(source, amount);
        return true;
    }

    /**
     * Plays the one-shot burst effect. The blast sound splits on what actually happened —
     * {@code livingSound} if someone got shocked this burst, {@code projectileSound} if only a
     * thrown item tripped it — and on a living hit, one of {@code hitSounds} additionally plays
     * from each hurt entity's own position (a close-up "zap" layered over the center-based blast),
     * picked at random per target for variety. The particle burst always plays either way.
     */
    private static void playTriggerEffect(ServerLevel level, AABB aabb, AnomalyType type,
                                           List<LivingEntity> hitLiving, boolean projectileTripped) {
        AnomalyTriggerEffect trigger = type.triggerEffect();
        if (trigger == null) return;

        if (trigger.particle() != null) {
            spawnParticlesInVolume(level, aabb, trigger.particle(), trigger.particleCount());
        }

        boolean livingHit = !hitLiving.isEmpty();
        ResourceLocation blastSound = livingHit ? trigger.livingSound() : trigger.projectileSound();
        if (blastSound != null) {
            playSound(level, aabb, blastSound, trigger.soundVolume(), trigger.soundPitch());
        }

        if (livingHit && !trigger.hitSounds().isEmpty()) {
            List<ResourceLocation> hitSounds = trigger.hitSounds();
            for (LivingEntity target : hitLiving) {
                ResourceLocation hitSound = hitSounds.get(level.random.nextInt(hitSounds.size()));
                playSoundAt(level, target.position(), hitSound, trigger.hitSoundVolume(), trigger.hitSoundPitch());
            }
        }
    }
}
