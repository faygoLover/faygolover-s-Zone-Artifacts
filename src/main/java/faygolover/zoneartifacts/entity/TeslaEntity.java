package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.TeslaBumpPacket;
import faygolover.zoneartifacts.network.TeslaElectrifyPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A Tesla: a small ball of lightning that flies a closed loop of GM-placed waypoints ({@link
 * TeslaRoute}), diverting to chase a flagged player within range, and "popping" the instant she
 * touches a living entity — dealing damage, electrifying the target for a moment, and dealing a
 * second hit at the end of it — before disappearing and respawning on her route after a delay.
 * <p>
 * She's a plain {@link Entity}, not a {@link LivingEntity}: she has no health and nothing can
 * damage her (the inherited {@link Entity#hurt} is a no-op unless a subclass overrides it, and
 * this one doesn't), and {@link #isPickable()} stays false so melee swings and projectiles never
 * even register her as a target — they simply pass through, which is the "let her be" the design
 * calls for. The only way to stop a chase is to make her hit a wall (see {@link #onBump}).
 * <p>
 * All movement is done by hand — a fresh {@link #move(MoverType, Vec3)} every tick toward whatever
 * the current target point is — rather than vanilla AI goals, since nothing about "always move in
 * a straight line at constant speed toward one point, and never react to being blocked except to
 * redirect" fits the goal-selector model. Real block collision still applies because {@code move}
 * always resolves it regardless of entity type; nothing here makes her solid to <em>other</em>
 * entities, so players and mobs simply fly through her body — the actual "did I touch someone"
 * check is her own manual overlap scan in {@link #tick()}, not collision response.
 */
public class TeslaEntity extends Entity {

    /** Minimum ticks between two bump-discharge events, so a Tesla stuck against an unreachable
     *  waypoint (bad GM placement) or ramming a wall mid-chase doesn't flood sound/network with a
     *  new discharge every single tick. */
    private static final int BUMP_COOLDOWN_TICKS = 10;

    /** How close (in blocks) counts as "arrived" at a patrol waypoint. */
    private static final double WAYPOINT_ARRIVAL_DISTANCE = 0.6;

    private static final EntityDataAccessor<String> DATA_TYPE_ID =
            SynchedEntityData.defineId(TeslaEntity.class, EntityDataSerializers.STRING);

    private int routeId;
    private ResourceLocation typeId = new ResourceLocation(ZoneArtifacts.MODID, "tesla");
    private int waypointIndex;
    private int ageTicks;
    private int bumpCooldown;

    @Nullable
    private UUID pursuingTarget;

    public TeslaEntity(EntityType<? extends TeslaEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    /** Called once, right after spawning, by {@link TeslaSavedData} — never on the client (a
     *  client-side instance only ever gets {@code typeId} from synced data, see {@link #typeId()}). */
    public void initRoute(int routeId, ResourceLocation typeId, int waypointIndex) {
        this.routeId = routeId;
        this.typeId = typeId;
        this.waypointIndex = waypointIndex;
        this.entityData.set(DATA_TYPE_ID, typeId.toString());
    }

    public int routeId() {
        return routeId;
    }

    /** Works on both sides: the server field is authoritative server-side, the synced copy is all
     *  the client ever has. */
    public ResourceLocation typeId() {
        if (this.level().isClientSide) {
            ResourceLocation parsed = ResourceLocation.tryParse(this.entityData.get(DATA_TYPE_ID));
            return parsed != null ? parsed : typeId;
        }
        return typeId;
    }

    /** Ticks since this exact instance spawned — resets to 0 on every respawn, since a respawn is
     *  a brand new entity. Drives the "growing out of a point" animation client-side. */
    public int ageTicks() {
        return ageTicks;
    }

    // ---- Entity boilerplate -------------------------------------------

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TYPE_ID, typeId.toString());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.routeId = tag.getInt("RouteId");
        String typeStr = tag.getString("TypeId");
        if (!typeStr.isEmpty()) {
            this.typeId = new ResourceLocation(typeStr);
        }
        this.waypointIndex = tag.getInt("WaypointIndex");
        this.entityData.set(DATA_TYPE_ID, this.typeId.toString());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("RouteId", routeId);
        tag.putString("TypeId", typeId.toString());
        tag.putInt("WaypointIndex", waypointIndex);
    }

    /** Never a valid attack/interact target — swings and projectile raytraces skip her as if she
     *  weren't there at all, which is what makes her genuinely untouchable rather than merely
     *  invulnerable. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    // ---- tick loop ------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        ageTicks++;

        if (this.level().isClientSide) {
            // Everything visible (position, the ball, a strike, a bump) is either vanilla entity
            // interpolation or driven by a server-sent packet — nothing to simulate here.
            return;
        }

        ServerLevel level = (ServerLevel) this.level();
        TeslaType type = TeslaTypeManager.get(typeId);
        if (type == null) {
            // Datapack removed/renamed this type; sit still rather than move on numbers that no
            // longer exist, or crash.
            return;
        }

        if (ageTicks <= type.growTicks()) {
            // Standing still, "growing" at the spawn point — no hit detection yet either, so she
            // can't pop again the instant she appears on top of whoever killed her last time.
            return;
        }

        if (tryStrikeNearbyLiving(level, type)) {
            return; // this instance was discarded inside strike(); nothing left to do.
        }

        updatePursuitTarget(level, type);
        moveTowardCurrentTarget(level, type);
    }

    private boolean tryStrikeNearbyLiving(ServerLevel level, TeslaType type) {
        List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox(), LivingEntity::isAlive);
        if (hits.isEmpty()) return false;
        strike(level, type, hits.get(0));
        return true;
    }

    /** The hit-then-electrify-then-die sequence: damage now, an electrify visual on the target for
     *  {@code electrifyTicks}, a second damage tick queued for when that ends (see {@link
     *  TeslaSavedData#schedulePendingHit}, since this instance won't exist by then), and this
     *  Tesla popping — discarded now, a fresh one scheduled on her route after {@code
     *  respawnTicks}. */
    private void strike(ServerLevel level, TeslaType type, LivingEntity target) {
        dealDamage(level, target, type.damageType(), type.damage());
        playImpactSounds(level, type, target);

        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),
                new TeslaElectrifyPacket(target.getId(), type.electrifyTicks(), type.arc().color()));

        TeslaSavedData data = TeslaSavedData.get(level);
        data.schedulePendingHit(target.getUUID(), type.damageType(), type.damage(), type.electrifyTicks());
        data.scheduleRespawn(routeId, type.respawnTicks());

        this.discard();
    }

    private void playImpactSounds(ServerLevel level, TeslaType type, LivingEntity target) {
        TeslaImpactSound impact = type.impact();
        if (impact.blastSound() != null) {
            playSoundAt(level, this.position(), impact.blastSound(), impact.blastVolume(), impact.blastPitch());
        }
        if (!impact.hitSounds().isEmpty()) {
            ResourceLocation hitSound = impact.hitSounds().get(level.random.nextInt(impact.hitSounds().size()));
            playSoundAt(level, target.position(), hitSound, impact.hitVolume(), impact.hitPitch());
        }
    }

    private static void playSoundAt(ServerLevel level, Vec3 pos, ResourceLocation soundId, float volume, float pitch) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound == null) return;
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Shared by the immediate hit in {@link #strike} and the delayed second hit in {@link
     *  TeslaSavedData} — both just need "resolve this damage type and apply it," nothing Tesla-
     *  instance-specific. */
    public static void dealDamage(ServerLevel level, LivingEntity target, ResourceLocation damageTypeId, float amount) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
        Optional<Holder.Reference<DamageType>> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key);
        if (holder.isEmpty()) return;
        target.hurt(new DamageSource(holder.get()), amount);
    }

    // ---- pursuit ----------------------------------------------------------

    /** Only re-evaluated while not already pursuing (per design, a pursuit runs until it lands a
     *  hit, loses the target, or gets dropped by a block bump — never re-targets mid-chase) and
     *  cleared the instant the target stops qualifying: gone, dead, or the flag was lifted. */
    private void updatePursuitTarget(ServerLevel level, TeslaType type) {
        if (pursuingTarget != null) {
            Entity current = level.getEntity(pursuingTarget);
            if (!(current instanceof Player player) || !player.isAlive() || !ArtifactFlag.isEquipped(player)) {
                pursuingTarget = null;
            }
            return;
        }

        double radius = type.aggroRadius();
        if (radius <= 0) return;
        double radiusSq = radius * radius;

        for (Player player : level.players()) {
            if (player.level() != level || !player.isAlive()) continue;
            if (!ArtifactFlag.isEquipped(player)) continue;
            if (this.distanceToSqr(player) <= radiusSq) {
                pursuingTarget = player.getUUID();
                break;
            }
        }
    }

    private void moveTowardCurrentTarget(ServerLevel level, TeslaType type) {
        boolean pursuing = pursuingTarget != null;
        Vec3 targetPos;
        Vec3 waypointCenter = null;

        if (pursuing) {
            Entity target = level.getEntity(pursuingTarget);
            if (target == null) {
                // Vanished between updatePursuitTarget and here (unlikely, but cheap to guard);
                // resolved cleanly as a patrol tick next time around.
                pursuingTarget = null;
                return;
            }
            targetPos = target.getEyePosition();
        } else {
            TeslaRoute route = TeslaSavedData.get(level).routeById(routeId);
            if (route == null || route.waypoints().isEmpty()) {
                return; // route was deleted out from under her; just hover until she's removed too
            }
            waypointIndex = Math.floorMod(waypointIndex, route.waypoints().size());
            waypointCenter = Vec3.atCenterOf(route.waypoints().get(waypointIndex));
            targetPos = waypointCenter;
        }

        Vec3 toTarget = targetPos.subtract(this.position());
        double distance = toTarget.length();
        Vec3 motion = distance < 1.0E-4 ? Vec3.ZERO : toTarget.scale(Math.min(type.speed(), distance) / distance);

        this.move(MoverType.SELF, motion);
        if (motion.lengthSqr() > 1.0E-8) {
            this.setYRot((float) (Mth.atan2(-motion.x, motion.z) * (180.0 / Math.PI)));
        }

        if (bumpCooldown > 0) {
            bumpCooldown--;
        } else if (this.horizontalCollision || this.verticalCollision) {
            onBump(level, type);
        }

        if (!pursuing && waypointCenter != null && this.position().closerThan(waypointCenter, WAYPOINT_ARRIVAL_DISTANCE)) {
            TeslaRoute route = TeslaSavedData.get(level).routeById(routeId);
            if (route != null && !route.waypoints().isEmpty()) {
                waypointIndex = (waypointIndex + 1) % route.waypoints().size();
            }
        }
    }

    /** Hitting solid terrain — whether idly patrolling or mid-chase — always does the same thing:
     *  a cosmetic discharge and dropping whatever pursuit was in progress, never a "death". She
     *  just redirects toward her current target fresh next tick. */
    private void onBump(ServerLevel level, TeslaType type) {
        bumpCooldown = BUMP_COOLDOWN_TICKS;
        pursuingTarget = null;

        TeslaBumpVisual bump = type.bump();
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),
                new TeslaBumpPacket(this.position(), type.arc().color(), bump.boltCount(), bump.reach(), bump.durationTicks()));
    }
}
