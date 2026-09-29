package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
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
import net.minecraft.world.phys.Vec3;
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

        boolean wasOnCooldown = instance.cooldownTicks() > 0;

        // tickBurst runs first so cooldownTicks reflects *this* tick's state before we decide
        // whether ambient gets to run — see its javadoc for why the field has to be updated every
        // tick rather than only while actively counting down.
        if (type.trigger().type() == AnomalyTrigger.TriggerType.BURST) {
            tickBurst(level, instance, type);
        }
        // PASSIVE_FIELD / PHASED: not implemented yet, see AnomalyTrigger's javadoc.

        boolean onCooldown = instance.cooldownTicks() > 0;
        if (wasOnCooldown && !onCooldown) {
            // Cooldown just ended: bring the hum/sparks back right away instead of waiting out
            // whatever was left on their interval timers when they got paused mid-count.
            instance.setAmbientParticleTicker(0);
            instance.setAmbientSoundTicker(0);
        }

        // While on cooldown, the anomaly should read as completely "spent" — no hum, no sparks —
        // until the recovery period fully ends.
        if (!onCooldown) {
            tickAmbient(level, instance, type);
        }
    }

    // ---- ambient (always-on) visual/sound ---------------------------------

    private static void tickAmbient(ServerLevel level, AnomalyInstance instance, AnomalyType type) {
        AnomalyVisualSound ambient = type.ambient();
        if (ambient == null) return;

        // Note: ambient.sound() isn't played from here. A one-shot server broadcast like this
        // can't be un-fired once sent — vanilla has no "stop that sound" counterpart to
        // ServerLevel.playSound — so a long idle hum kept playing to the end even after the
        // anomaly triggered or was removed. The idle loop is now run entirely client-side as a
        // real, stoppable SoundInstance (see AnomalyAmbientSoundHandler), driven by the
        // onCooldown flag synced in SyncAnomaliesPacket and the sound info synced in
        // SyncAnomalyTypeShapesPacket. This method only ever handles particles now.
        if (ambient.particle() != null && ambient.intervalTicks() > 0) {
            AABB aabb = AnomalyGeometry.zoneAabb(instance, type);
            int t = instance.ambientParticleTicker() - 1;
            if (t <= 0) {
                spawnParticlesOnSurfaces(level, aabb, ambient);
                t = ambient.intervalTicks();
            }
            instance.setAmbientParticleTicker(t);
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

    /** How far off the block face the spark sits, pulled back into the empty (zone-interior) cell
     *  so it renders as a visible point in open space instead of half-clipped into the neighboring
     *  solid block's geometry. */
    private static final double SURFACE_INSET = 0.125; // 1/8 block

    /** Half-width of the in-plane scatter across the face. Kept tight so sparks stay hugging the
     *  actual boundary the zone touches rather than smearing across the whole block face — the
     *  zone's real edge should read clearly instead of looking like a fuzzy cloud. */
    private static final double SURFACE_JITTER = 0.18;

    private static Vec3Point faceMidpoint(BlockPos emptyPos, Direction dir, ServerLevel level) {
        double faceOffset = 0.5 - SURFACE_INSET;
        double baseX = emptyPos.getX() + 0.5 + dir.getStepX() * faceOffset;
        double baseY = emptyPos.getY() + 0.5 + dir.getStepY() * faceOffset;
        double baseZ = emptyPos.getZ() + 0.5 + dir.getStepZ() * faceOffset;

        Direction.Axis axis = dir.getAxis();
        double jx = axis == Direction.Axis.X ? 0 : (level.random.nextDouble() - 0.5) * SURFACE_JITTER;
        double jy = axis == Direction.Axis.Y ? 0 : (level.random.nextDouble() - 0.5) * SURFACE_JITTER;
        double jz = axis == Direction.Axis.Z ? 0 : (level.random.nextDouble() - 0.5) * SURFACE_JITTER;

        return new Vec3Point(baseX + jx, baseY + jy, baseZ + jz);
    }

    /** Used for the one-shot trigger burst, where a diffuse mid-air flash reads fine — unlike the
     *  ambient hum, it isn't meant to look surface-anchored. */
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

        // Always write the decremented value back, even once it reaches 0: previously this only
        // called setCooldownTicks while still counting down (cooldown > 0), so the stored field
        // froze at 1 forever after the tick it hit zero — tickBurst's own local "cooldown > 0"
        // check kept recomputing 1 - 1 = 0 every tick (so burst detection still worked), but
        // instance.cooldownTicks() itself never became <= 0 again. That silently broke the new
        // ambient cooldown-gate in tickInstance: it reads instance.cooldownTicks() directly, so it
        // saw a permanent "1" and never let ambient particles/sound resume after the first fire.
        int cooldown = Math.max(0, instance.cooldownTicks() - 1);
        instance.setCooldownTicks(cooldown);

        if (wasOnCooldown && cooldown == 0) {
            // Cooldown just fully ended: the client-side idle-loop handler (AnomalyAmbientSoundHandler)
            // decides whether to play based on this synced "on cooldown" flag, so it needs telling
            // the instant this flips or the ambient hum would stay silent even though the server
            // itself has already resumed spawning particles/sound.
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
            // Cooldown just started: tell the client right away so it can stop the idle loop the
            // moment the anomaly fires, rather than waiting for some unrelated future sync.
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
