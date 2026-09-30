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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
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
    /** The zone being ticked right now (its KPK "damage" switch decides), or null. */
    @javax.annotation.Nullable
    private static AnomalyInstance current;

    public static void setCurrent(@javax.annotation.Nullable AnomalyInstance instance) {
        current = instance;
    }

    /** A creative player whom this may leave alone (unless the zone at work acts on creative players too). */
    public static boolean creativeExempt(net.minecraft.world.entity.Entity e) {
        return e instanceof Player p && p.isCreative() && !targetsGm(e);
    }

    /** A spectator whom this may leave alone (unless the zone at work acts on spectators too). */
    public static boolean spectatorExempt(net.minecraft.world.entity.Entity e) {
        return e.isSpectator() && !targetsGm(e);
    }

    private static boolean targetsGm(net.minecraft.world.entity.Entity e) {
        if (current != null) return current.targetsGm();
        if (!(e.level() instanceof ServerLevel level)) return false;
        for (AnomalyInstance instance : AnomalySavedData.get(level).instances()) {
            if (instance.enabled() && instance.targetsGm() && AnomalyGeometry.zoneAabb(instance).inflate(1.0).contains(e.position())) return true;
        }
        return false;
    }

    /** Outside a zone's own tick (lingering clouds, the swamp's choking…): harmless if it stands in a zone set harmless. */
    private static boolean harmlessHere(ServerLevel level, LivingEntity target) {
        for (AnomalyInstance instance : AnomalySavedData.get(level).instances()) {
            if (!instance.harmful() && AnomalyGeometry.zoneAabb(instance).inflate(1.0).contains(target.position())) return true;
        }
        return false;
    }

    public static boolean hurt(ServerLevel level, LivingEntity target, ResourceLocation damageTypeId, float amount) {
        if (amount <= 0) return true;
        // Set harmless in the KPK: everything else happens as usual (the bolts, the shock, the sounds) — only no damage.
        if (current != null ? !current.harmful() : harmlessHere(level, target)) return true;
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
        Optional<Holder.Reference<DamageType>> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key);
        if (holder.isEmpty()) return false;
        target.hurt(new DamageSource(holder.get()), amount);
        return true;
    }

    /** Like {@link #hurt(ServerLevel, LivingEntity, ResourceLocation, float)}, and a mob that
     *  survives runs away from {@code from} ({@link #flee}). */
    public static boolean hurt(ServerLevel level, LivingEntity target, ResourceLocation damageTypeId, float amount, Vec3 from) {
        boolean done = hurt(level, target, damageTypeId, amount);
        if (done && amount > 0) flee(target, from);
        return done;
    }

    private static final String FLEE_TAG = "fl_zone_arts_fled";

    /**
     * A mob hurt by an anomaly gets away from it. Vanilla animals only panic when someone hit them
     * (or they burn or freeze), and an anomaly is no one, so they used to just stand in it.
     * Peaceful mobs bolt like after a player's hit; hostile ones only step out at a walk and are
     * back after their target at once. At most once a second per mob.
     */
    public static void flee(LivingEntity target, Vec3 from) {
        if (!(target instanceof PathfinderMob mob) || !mob.isAlive() || mob.isPassenger()) return;
        long now = mob.level().getGameTime();
        CompoundTag data = mob.getPersistentData();
        if (now - data.getLong(FLEE_TAG) < 20 && data.contains(FLEE_TAG)) return;
        data.putLong(FLEE_TAG, now);
        Vec3 away = DefaultRandomPos.getPosAway(mob, 10, 5, from);
        if (away == null) {
            Vec3 dir = mob.position().subtract(from);
            dir = new Vec3(dir.x, 0.0, dir.z);
            if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(mob.getRandom().nextDouble() - 0.5, 0.0, mob.getRandom().nextDouble() - 0.5);
            away = mob.position().add(dir.normalize().scale(8.0));
        }
        mob.getNavigation().moveTo(away.x, away.y, away.z, mob instanceof Enemy ? 1.0 : 1.6);
    }

    public static void playSound(ServerLevel level, Vec3 pos, ResourceLocation soundId, float volume) {
        playSound(level, pos, soundId, volume, 1.0f);
    }

    public static void playSound(ServerLevel level, Vec3 pos, ResourceLocation soundId, float volume, float pitch) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound == null) return;
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.AMBIENT, volume, pitch);
    }

    public static void playRandom(ServerLevel level, Vec3 pos, List<ResourceLocation> sounds, float volume) {
        if (sounds.isEmpty()) return;
        playSound(level, pos, sounds.get(level.random.nextInt(sounds.size())), volume);
    }
}
