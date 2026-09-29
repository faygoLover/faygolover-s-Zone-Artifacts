package faygolover.zoneartifacts.tesla;

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

/** Damage and sound helpers shared by the Tesla entity and the delayed second hit. */
public final class TeslaCombat {

    private TeslaCombat() {
    }

    /** Players and mobs, same rules as the other anomalies; spectators and creative players are
     *  left alone so a GM building routes next to a Tesla doesn't keep popping it. */
    public static boolean isValidTarget(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        return !(entity instanceof Player player && player.isCreative());
    }

    /** Same approach as Electra: DamageSources' own helper for custom types is private, so the
     *  source is built straight from the damage-type registry holder. */
    public static boolean hurt(ServerLevel level, LivingEntity target, ResourceLocation damageTypeId, float amount) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
        Optional<Holder.Reference<DamageType>> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key);
        if (holder.isEmpty()) return false;
        return target.hurt(new DamageSource(holder.get()), amount);
    }

    public static void playSound(ServerLevel level, Vec3 pos, ResourceLocation soundId, float volume) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound == null) return;
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.AMBIENT, volume, 1.0f);
    }

    public static void playRandomHit(ServerLevel level, Vec3 pos, TeslaConfig config) {
        List<ResourceLocation> hits = config.hitSounds();
        if (hits.isEmpty()) return;
        playSound(level, pos, hits.get(level.random.nextInt(hits.size())), config.soundVolume());
    }
}
