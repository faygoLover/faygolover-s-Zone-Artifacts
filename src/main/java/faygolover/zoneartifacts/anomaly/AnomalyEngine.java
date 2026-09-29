package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives every placed anomaly once per level tick: cooldown countdown, zone-entry detection for
 * {@link faygolover.zoneartifacts.anomaly.AnomalyTrigger.TriggerType#BURST} anomalies, damage, and
 * the network broadcasts that tell clients what happened (cooldown flips full-on/full-off, and
 * per-target strikes for {@code AnomalyArcRenderer} to render as real lightning bolts).
 * <p>
 * Server-authoritative throughout: this is the only place damage is applied or cooldowns actually
 * change — clients only ever render what this class tells them happened.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class AnomalyEngine {

    private AnomalyEngine() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.side != LogicalSide.SERVER) return;
        if (!(event.level instanceof ServerLevel level)) return;

        AnomalySavedData data = AnomalySavedData.get(level);
        // Copy first: triggering an effect never mutates the instance list itself, but this keeps
        // the tick loop safe even if a future trigger type ever does (e.g. a self-destructing one).
        for (AnomalyInstance instance : List.copyOf(data.instances())) {
            tick(level, instance);
        }
    }

    private static void tick(ServerLevel level, AnomalyInstance instance) {
        AnomalyType type = AnomalyTypeManager.get(instance.typeId());
        if (type == null) return; // datapack no longer defines this type; instance just sits inert

        if (type.trigger().type() != AnomalyTrigger.TriggerType.BURST) {
            // PASSIVE_FIELD / PHASED are parsed but not yet acted on, see AnomalyTrigger's javadoc.
            return;
        }
        tickBurst(level, instance, type);
    }

    private static void tickBurst(ServerLevel level, AnomalyInstance instance, AnomalyType type) {
        boolean wasOnCooldown = instance.cooldownTicks() > 0;

        // Always write the decremented value back, even once it lands exactly on 0 — leaving this
        // conditional on "cooldown > 0" was the original bug: on the tick it first reached 0 the
        // write was skipped, so the instance froze on cooldown forever.
        int cooldown = Math.max(0, instance.cooldownTicks() - 1);
        instance.setCooldownTicks(cooldown);

        boolean nowOnCooldown = cooldown > 0;
        if (wasOnCooldown && !nowOnCooldown) {
            AnomalySyncHandler.broadcastCooldown(level, instance, false);
        }
        if (nowOnCooldown) {
            return; // still recovering — "полностью себя никак не проявляет"
        }

        AABB aabb = AnomalyGeometry.zoneAabb(instance, type);
        AnomalyDetect detect = type.detect();

        List<LivingEntity> hitLiving = new ArrayList<>();
        List<Projectile> hitProjectiles = new ArrayList<>();

        if (detect.players() || detect.mobs()) {
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, aabb)) {
                if (!living.isAlive()) continue;
                boolean isPlayer = living instanceof Player;
                if (isPlayer && !detect.players()) continue;
                if (!isPlayer && !detect.mobs()) continue;
                hitLiving.add(living);
            }
        }
        if (detect.thrownProjectiles()) {
            hitProjectiles.addAll(level.getEntitiesOfClass(Projectile.class, aabb));
        }

        if (hitLiving.isEmpty() && hitProjectiles.isEmpty()) return;

        for (LivingEntity living : hitLiving) {
            applyDamage(level, instance, type, living);
        }

        playTriggerEffect(level, instance, type, hitLiving, hitProjectiles);

        instance.setCooldownTicks(type.trigger().cooldownTicks());
        AnomalySyncHandler.broadcastCooldown(level, instance, true);
    }

    private static boolean applyDamage(ServerLevel level, AnomalyInstance instance, AnomalyType type, LivingEntity target) {
        ResourceLocation damageTypeId = type.effect().damageType();
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key)
                .orElse(null);
        if (holder == null) {
            return false; // damage_type json missing/typo'd — fail closed rather than crash the tick
        }

        // DamageSources.source(ResourceKey) is private in 1.20.1, so the DamageSource is built by
        // hand from the registry Holder instead of going through that convenience method.
        DamageSource source = new DamageSource(holder);
        float amount = type.effect().damageForLevel(instance.level());
        return target.hurt(source, amount);
    }

    private static void playTriggerEffect(ServerLevel level, AnomalyInstance instance, AnomalyType type,
                                           List<LivingEntity> hitLiving, List<Projectile> hitProjectiles) {
        // Every hit target becomes its own real strike — a bolt per living entity and per
        // projectile, never collapsed down to a single zone-center sentinel strike.
        for (LivingEntity living : hitLiving) {
            AnomalySyncHandler.broadcastStrike(level, instance, living.getId());
        }
        for (Projectile projectile : hitProjectiles) {
            AnomalySyncHandler.broadcastStrike(level, instance, projectile.getId());
        }

        AnomalyTriggerEffect fx = type.triggerEffect();
        if (fx == null) return;

        Vec3 center = AnomalyGeometry.zoneAabb(instance, type).getCenter();
        ResourceLocation blastSoundId = hitLiving.isEmpty() ? fx.projectileSound() : fx.livingSound();
        playSoundIfPresent(level, blastSoundId, center.x, center.y, center.z, fx.soundVolume(), fx.soundPitch());

        if (!fx.hitSounds().isEmpty()) {
            for (LivingEntity living : hitLiving) {
                ResourceLocation hitSoundId = fx.hitSounds().get(level.getRandom().nextInt(fx.hitSounds().size()));
                playSoundIfPresent(level, hitSoundId, living.getX(), living.getY(), living.getZ(),
                        fx.hitSoundVolume(), fx.hitSoundPitch());
            }
        }
    }

    private static void playSoundIfPresent(ServerLevel level, ResourceLocation soundId,
                                            double x, double y, double z, float volume, float pitch) {
        if (soundId == null) return;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound == null) return;
        level.playSound(null, x, y, z, sound, SoundSource.HOSTILE, volume, pitch);
    }
}
