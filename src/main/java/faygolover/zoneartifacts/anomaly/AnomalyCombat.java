package faygolover.zoneartifacts.anomaly;

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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

/** Damage and sound helpers shared by all anomalies (Electra, Tesla, the electrify command). */
public final class AnomalyCombat {

    private AnomalyCombat() {
    }

    /** Living, not a spectator, not in creative. Used by the Tesla (so a GM building routes next
     *  to one doesn't keep popping it) and by the electrify command. */
    public static boolean isValidTeslaTarget(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        return !(entity instanceof Player player && player.isCreative());
    }

    /**
     * Hurts {@code target} with a data-driven damage type. DamageSources' own helper for custom
     * types is private, so the source is built straight from the damage-type registry holder.
     * The damage type itself ({@code data/fl_zone_arts/damage_type/anomaly_shock.json}) is the one
     * JSON that has to stay: it's how Minecraft 1.20 registers damage types at all.
     *
     * @return whether the damage type was found and the hit was attempted
     */
    public static boolean hurt(ServerLevel level, LivingEntity target, ResourceLocation damageTypeId, float amount) {
        if (amount <= 0) return true;
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
        Optional<Holder.Reference<DamageType>> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key);
        if (holder.isEmpty()) return false;
        target.hurt(new DamageSource(holder.get()), amount);
        return true;
    }

    public static void playSound(ServerLevel level, Vec3 pos, ResourceLocation soundId, float volume) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound == null) return;
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.AMBIENT, volume, 1.0f);
    }

    public static void playRandom(ServerLevel level, Vec3 pos, List<ResourceLocation> sounds, float volume) {
        if (sounds.isEmpty()) return;
        playSound(level, pos, sounds.get(level.random.nextInt(sounds.size())), volume);
    }
}
