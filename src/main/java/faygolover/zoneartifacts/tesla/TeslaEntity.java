package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.network.TeslaSyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A roaming "ball of lightning" that flies a closed waypoint loop (or sits still, for a
 * single-point route), chases any living entity that enters its detection radius indefinitely
 * until it loses them or hits a solid block, and is otherwise completely unkillable and
 * unpushable — projectiles, melee and every other player-initiated interaction pass straight
 * through it ({@link #isPickable()}/{@link #canBeHitByProjectile()} both return {@code false}, so
 * vanilla's own targeting never resolves a hit on it in the first place, rather than resolving a
 * hit that is then merely ignored).
 * <p>
 * No AI/pathfinding goals at all — movement is a straight-line scripted step towards either the
 * current chase target or the next waypoint, driven entirely from {@link #tick()}. Collision
 * against a real block is our own manual check (a normal {@link Entity#move} is never called),
 * since a genuine collision is what "kills" it: it drops whatever it was chasing, sits invisible
 * for {@code respawnDelayTicks}, then "grows" back in place over {@code growTicks} before moving
 * again.
 */
public class TeslaEntity extends Entity {

    public static final byte STATE_NORMAL = 0;
    public static final byte STATE_RESPAWNING = 1;
    public static final byte STATE_GROWING = 2;

    private static final int ELECTRIFY_DURATION_TICKS = 20;

    private static final TeslaType FALLBACK_TYPE = new TeslaType(
            TeslaTypeIds.TESLA, new ResourceLocation("minecraft", "generic"),
            4.0f, 0.1f, 10.0, 80, 20, 0x8FE8FF,
            null, 1.0f, 1.0f, null, List.of(), 1.0f, 1.0f, 8, 2.0);

    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(TeslaEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_GROW_PROGRESS =
            SynchedEntityData.defineId(TeslaEntity.class, EntityDataSerializers.FLOAT);

    private ResourceLocation typeId = TeslaTypeIds.TESLA;
    @Nullable
    private UUID routeId;
    private List<BlockPos> waypoints = new ArrayList<>();
    private int currentWaypointIndex = 0;
    private int stateTicksRemaining = 0;

    @Nullable
    private LivingEntity chaseTarget;
    private final Map<UUID, Long> electrifying = new HashMap<>();

    public TeslaEntity(EntityType<? extends TeslaEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true; // manual scripted movement only; our own block-collision check "kills" it, not vanilla physics
    }

    public void initRoute(UUID routeId, List<BlockPos> waypoints) {
        this.routeId = routeId;
        this.waypoints = new ArrayList<>(waypoints);
        this.currentWaypointIndex = 0;
        if (!waypoints.isEmpty()) {
            BlockPos start = waypoints.get(0);
            this.setPos(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
        }
    }

    @Nullable
    public UUID routeId() {
        return routeId;
    }

    private TeslaType type() {
        TeslaType found = TeslaTypeManager.get(typeId);
        return found != null ? found : FALLBACK_TYPE;
    }

    // ---- synced visual state --------------------------------------------------

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STATE, STATE_NORMAL);
        this.entityData.define(DATA_GROW_PROGRESS, 1.0f);
    }

    public byte getTeslaState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setTeslaState(byte state) {
        this.entityData.set(DATA_STATE, state);
    }

    public float getGrowProgress() {
        return this.entityData.get(DATA_GROW_PROGRESS);
    }

    private void setGrowProgress(float progress) {
        this.entityData.set(DATA_GROW_PROGRESS, progress);
    }

    // ---- immunity: nothing player-initiated ever touches it -------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false; // player attack/interact raytraces never resolve a hit on it at all
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false; // arrows/thrown items pass straight through instead of being ignored on hit
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(0.8f, 0.8f);
    }

    // ---- tick loop --------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            serverTick(serverLevel);
        }
    }

    private void serverTick(ServerLevel level) {
        switch (getTeslaState()) {
            case STATE_RESPAWNING -> tickRespawning();
            case STATE_GROWING -> tickGrowing();
            default -> tickNormal(level);
        }
    }

    private void tickRespawning() {
        stateTicksRemaining--;
        if (stateTicksRemaining <= 0) {
            setTeslaState(STATE_GROWING);
            stateTicksRemaining = Math.max(1, type().growTicks());
            setGrowProgress(0.0f);
        }
    }

    private void tickGrowing() {
        int total = Math.max(1, type().growTicks());
        stateTicksRemaining--;
        float progress = 1.0f - Math.max(0, stateTicksRemaining) / (float) total;
        setGrowProgress(Math.min(1.0f, progress));
        if (stateTicksRemaining <= 0) {
            setTeslaState(STATE_NORMAL);
            setGrowProgress(1.0f);
        }
    }

    private void tickNormal(ServerLevel level) {
        updateChaseTarget(level);

        Vec3 dir = computeDesiredDirection();
        if (!dir.equals(Vec3.ZERO)) {
            double speed = type().speed();
            Vec3 delta = dir.scale(speed);
            Vec3 nextPos = this.position().add(delta);
            AABB nextBox = this.getBoundingBox().move(delta);
            if (!level.noCollision(this, nextBox)) {
                die(level, this.position());
                return;
            }
            this.setPos(nextPos.x, nextPos.y, nextPos.z);
        }

        advanceIfReached();
        tickContactDamage(level);
    }

    private Vec3 computeDesiredDirection() {
        Vec3 targetPos;
        if (chaseTarget != null) {
            targetPos = chaseTarget.position();
        } else {
            BlockPos wp = currentWaypoint();
            if (wp == null) return Vec3.ZERO;
            targetPos = Vec3.atCenterOf(wp);
        }
        Vec3 diff = targetPos.subtract(this.position());
        if (diff.lengthSqr() < 1.0E-4) return Vec3.ZERO;
        return diff.normalize();
    }

    @Nullable
    private BlockPos currentWaypoint() {
        if (waypoints.isEmpty()) return null;
        return waypoints.get(Math.floorMod(currentWaypointIndex, waypoints.size()));
    }

    private void advanceIfReached() {
        if (chaseTarget != null) return; // only the patrol index advances; chasing overrides it entirely
        BlockPos wp = currentWaypoint();
        if (wp == null) return;
        double threshold = Math.max(0.25, type().speed() * 1.5);
        if (this.position().closerThan(Vec3.atCenterOf(wp), threshold)) {
            currentWaypointIndex = Math.floorMod(currentWaypointIndex + 1, Math.max(1, waypoints.size()));
        }
    }

    private void updateChaseTarget(ServerLevel level) {
        TeslaType type = type();
        if (chaseTarget != null) {
            if (!chaseTarget.isAlive() || this.distanceTo(chaseTarget) > type.detectRadius()) {
                chaseTarget = null;
            } else {
                return;
            }
        }

        double radius = type.detectRadius();
        // No "e != this" check needed: TeslaEntity extends Entity directly, not LivingEntity, so
        // it can never appear in its own getEntitiesOfClass(LivingEntity.class, ...) results in
        // the first place (in fact javac rejects that comparison outright as incomparable types).
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                e -> e.isAlive() && !(e instanceof Player p && p.isSpectator()));

        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        double radiusSq = radius * radius;
        for (LivingEntity candidate : nearby) {
            double distSq = this.distanceToSqr(candidate);
            if (distSq <= radiusSq && distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = candidate;
            }
        }
        chaseTarget = nearest;
    }

    /** A real block collision: the one and only way to stop this thing. */
    private void die(ServerLevel level, Vec3 at) {
        TeslaType type = type();
        TeslaSyncHandler.broadcastBlockBurst(level, at, type.blockBurstRayCount(), type.blockBurstDistance(), type.color());

        chaseTarget = null;
        electrifying.clear();
        setTeslaState(STATE_RESPAWNING);
        stateTicksRemaining = Math.max(1, type.respawnDelayTicks());
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** Damage on contact, twice per encounter: once immediately, once again when the 1s electrify effect ends. */
    private void tickContactDamage(ServerLevel level) {
        TeslaType type = type();
        List<LivingEntity> touching = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.15),
                e -> e.isAlive() && !(e instanceof Player p && p.isSpectator()));

        for (LivingEntity target : touching) {
            if (!electrifying.containsKey(target.getUUID())) {
                applyDamage(level, target, type);
                electrifying.put(target.getUUID(), level.getGameTime() + ELECTRIFY_DURATION_TICKS);
                TeslaSyncHandler.broadcastElectrify(level, this, target.getId(), ELECTRIFY_DURATION_TICKS);
                playContactSounds(level, target, type);
            }
        }

        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = electrifying.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now >= entry.getValue()) {
                LivingEntity target = findLivingByUuid(level, entry.getKey());
                if (target != null && target.isAlive()) {
                    applyDamage(level, target, type);
                }
                it.remove();
            }
        }
    }

    @Nullable
    private static LivingEntity findLivingByUuid(ServerLevel level, UUID uuid) {
        Entity found = level.getEntity(uuid);
        return found instanceof LivingEntity living ? living : null;
    }

    private void applyDamage(ServerLevel level, LivingEntity target, TeslaType type) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, type.damageType());
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(key)
                .orElse(null);
        if (holder == null) return;

        DamageSource source = new DamageSource(holder);
        target.hurt(source, type.damage());
    }

    private void playContactSounds(ServerLevel level, LivingEntity target, TeslaType type) {
        playSoundIfPresent(level, type.livingHitSound(), target.getX(), target.getY(), target.getZ(), 1.0f, 1.0f);
        if (!type.hitSounds().isEmpty()) {
            ResourceLocation soundId = type.hitSounds().get(level.getRandom().nextInt(type.hitSounds().size()));
            playSoundIfPresent(level, soundId, target.getX(), target.getY(), target.getZ(),
                    type.hitSoundVolume(), type.hitSoundPitch());
        }
    }

    private static void playSoundIfPresent(ServerLevel level, @Nullable ResourceLocation soundId,
                                            double x, double y, double z, float volume, float pitch) {
        if (soundId == null) return;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound == null) return;
        level.playSound(null, x, y, z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ---- persistence --------------------------------------------------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("type")) {
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("type"));
            if (parsed != null) typeId = parsed;
        }
        routeId = tag.contains("route_id") ? tag.getUUID("route_id") : null;

        waypoints.clear();
        ListTag list = tag.getList("waypoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            waypoints.add(new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z")));
        }
        currentWaypointIndex = tag.getInt("waypoint_index");
        setTeslaState(tag.getByte("tesla_state"));
        stateTicksRemaining = tag.getInt("state_ticks");
        setGrowProgress(tag.getFloat("grow_progress"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("type", typeId.toString());
        if (routeId != null) {
            tag.putUUID("route_id", routeId);
        }
        ListTag list = new ListTag();
        for (BlockPos p : waypoints) {
            CompoundTag pTag = new CompoundTag();
            pTag.putInt("x", p.getX());
            pTag.putInt("y", p.getY());
            pTag.putInt("z", p.getZ());
            list.add(pTag);
        }
        tag.put("waypoints", list);
        tag.putInt("waypoint_index", currentWaypointIndex);
        tag.putByte("tesla_state", getTeslaState());
        tag.putInt("state_ticks", stateTicksRemaining);
        tag.putFloat("grow_progress", getGrowProgress());
    }
}
