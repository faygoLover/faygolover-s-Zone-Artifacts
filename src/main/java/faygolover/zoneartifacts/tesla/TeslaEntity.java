package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyCombat;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.ElectrifyPacket;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.TeslaBurstPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The Tesla: a ball of lightning flying a closed route of waypoints ({@link TeslaRoute}).
 * <p>
 * A plain {@link Entity}, not a mob: no AI, no pathfinding, no gravity, and deliberately
 * untouchable — it can't be damaged, pushed, clicked, or hit by projectiles (it isn't pickable,
 * so arrows, snowballs and melee all pass straight through). The only ways to get rid of one are
 * to outrun it or to make it fly into a block.
 * <p>
 * Its settings (size, speed, respawn delay, damage, intensity) live on the route and are re-read
 * every tick, so tuner changes apply immediately; size and intensity are synced to clients for
 * rendering, and the size also scales the hitbox.
 * <p>
 * Life cycle (server-driven, the state is synced to clients for visuals and the idle sound):
 * <ol>
 *     <li>{@link State#SPAWNING}: stands on a waypoint and "grows out of a point".</li>
 *     <li>{@link State#PATROL}: flies waypoint to waypoint in a loop (a one-point route just hovers).</li>
 *     <li>{@link State#CHASE}: a player flagged {@code artifact_equipped} came within the chase
 *     radius — flies straight at them until the target is lost.</li>
 *     <li>{@link State#DEAD}: popped, after hitting a block (bolts scatter from the impact point)
 *     or touching a living entity (one hit + a purely visual electrification of the target).
 *     Invisible and inert until it respawns on a random waypoint of its route.</li>
 * </ol>
 */
public class TeslaEntity extends Entity {

    public enum State {
        SPAWNING, PATROL, CHASE, DEAD;

        public boolean isVisible() {
            return this != DEAD;
        }
    }

    /** Persistent-data key for the placeholder "wears an artifact" flag, set by command until the
     *  artifact system exists. */
    public static final String ARTIFACT_FLAG = ZoneArtifacts.MODID + "_artifact_equipped";

    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(TeslaEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(TeslaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_INTENSITY =
            SynchedEntityData.defineId(TeslaEntity.class, EntityDataSerializers.INT);

    private static final int CHASE_SCAN_INTERVAL = 5;
    private static final int POSITION_REPORT_INTERVAL = 20;

    /** How close counts as "touching" — a hair more than the hitbox itself. */
    private static final double CONTACT_MARGIN = 0.05;

    private int routeId;
    private int stateTicks;
    private int targetIndex;

    /** Server only, never saved: a reload simply resumes patrolling and re-acquires the target. */
    @Nullable
    private UUID chaseTargetUuid;

    /** Client only: {@link #tickCount} when the synced state last changed, for spawn animation. */
    private int clientStateChangeTick;

    /** Client only: smooth flight between position updates (a plain Entity just jumps to them). */
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public TeslaEntity(EntityType<? extends TeslaEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    // ---- flag helpers --------------------------------------------------------

    public static boolean hasArtifactFlag(Player player) {
        return player.getPersistentData().getBoolean(ARTIFACT_FLAG);
    }

    public static void setArtifactFlag(Player player, boolean value) {
        player.getPersistentData().putBoolean(ARTIFACT_FLAG, value);
    }

    // ---- setup ------------------------------------------------------------------

    /** Called once when the route is completed, before the entity is added to the level. */
    public void initOnRoute(TeslaRoute route) {
        List<BlockPos> waypoints = route.waypoints();
        this.routeId = route.id();
        this.targetIndex = waypoints.size() > 1 ? 1 : 0;
        applySettings(route);
        setCenter(TeslaGeometry.center(waypoints.get(0)));
        setState(State.SPAWNING);
    }

    public int routeId() {
        return routeId;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STATE, (byte) State.SPAWNING.ordinal());
        this.entityData.define(DATA_SIZE, (float) Tesla.DEFAULT_SIZE);
        this.entityData.define(DATA_INTENSITY, 3);
    }

    public State getState() {
        int ordinal = this.entityData.get(DATA_STATE);
        State[] values = State.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : State.PATROL;
    }

    private void setState(State state) {
        this.entityData.set(DATA_STATE, (byte) state.ordinal());
        this.stateTicks = 0;
    }

    /** Visible ball diameter in blocks (1 = standard). */
    public float getSize() {
        return Math.max(0.1f, this.entityData.get(DATA_SIZE));
    }

    public int getIntensity() {
        return this.entityData.get(DATA_INTENSITY);
    }

    /** Copies the route's size and intensity into the synced data when they changed. */
    private void applySettings(TeslaRoute route) {
        float size = (float) route.size();
        if (Math.abs(size - this.entityData.get(DATA_SIZE)) > 1.0E-4f) {
            this.entityData.set(DATA_SIZE, size);
        }
        if (route.intensity() != this.entityData.get(DATA_INTENSITY)) {
            this.entityData.set(DATA_INTENSITY, route.intensity());
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getSize());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key) && this.level().isClientSide) {
            this.clientStateChangeTick = this.tickCount;
        }
        if (DATA_SIZE.equals(key)) {
            // Resize around the ball's center, not its bottom, so it grows/shrinks in place.
            Vec3 c = center();
            refreshDimensions();
            this.setPos(c.x, c.y - this.getBbHeight() / 2.0, c.z);
        }
    }

    /** Client side: ticks (plus partial tick) spent in the current state, for the spawn animation. */
    public float clientStateAge(float partialTick) {
        return this.tickCount - this.clientStateChangeTick + partialTick;
    }

    public Vec3 center() {
        return this.position().add(0.0, this.getBbHeight() / 2.0, 0.0);
    }

    private void setCenter(Vec3 center) {
        this.moveTo(center.x, center.y - this.getBbHeight() / 2.0, center.z);
    }

    // ---- ticking ----------------------------------------------------------------

    @Override
    public void tick() {
        if (this.level().isClientSide && this.lerpSteps > 0) {
            this.setPos(getX() + (lerpX - getX()) / lerpSteps,
                    getY() + (lerpY - getY()) / lerpSteps,
                    getZ() + (lerpZ - getZ()) / lerpSteps);
            this.lerpSteps--;
        }
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverTick(serverLevel);
        }
    }

    /** Glide to server positions over the given steps instead of jumping; a big jump (respawn on
     *  another waypoint) still snaps. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        if (this.distanceToSqr(x, y, z) > 4.0) {
            this.setPos(x, y, z);
            this.lerpSteps = 0;
            return;
        }
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpSteps = Math.max(1, steps);
    }

    private void serverTick(ServerLevel level) {
        TeslaRouteSavedData data = TeslaRouteSavedData.get(level);
        TeslaRoute route = data.get(routeId);
        if (route == null || route.waypoints().isEmpty()) {
            // The route was removed (left-click or cleanup) while this Tesla sat in an unloaded chunk.
            discard();
            return;
        }
        if (route.teslaUuid() == null) {
            route.setTeslaUuid(getUUID());
            data.setDirty();
        } else if (!route.teslaUuid().equals(getUUID())) {
            discard();
            return;
        }
        if (this.tickCount % POSITION_REPORT_INTERVAL == 0) {
            BlockPos here = blockPosition();
            if (!here.equals(route.lastKnownTeslaPos())) {
                route.setLastKnownTeslaPos(here);
                data.setDirty();
            }
        }

        applySettings(route);
        if (targetIndex >= route.waypoints().size()) targetIndex = 0;
        stateTicks++;

        switch (getState()) {
            case SPAWNING -> {
                if (stateTicks >= Tesla.SPAWN_GROW_TICKS) setState(State.PATROL);
            }
            case PATROL, CHASE -> tickActive(level, route);
            case DEAD -> {
                if (stateTicks >= Math.max(1, route.respawnSeconds()) * 20) respawn(route);
            }
        }
    }

    private void tickActive(ServerLevel level, TeslaRoute route) {
        updateChaseTarget(level, route);

        Player target = getState() == State.CHASE ? resolveTarget(level) : null;
        Vec3 dest = target != null
                ? chaseDestination(route, target)
                : TeslaGeometry.center(route.waypoints().get(targetIndex));

        double speed = route.kind().baseSpeed() * route.speedMultiplier();
        Vec3 toDest = dest.subtract(center());
        double dist = toDest.length();
        if (dist > 1.0E-4 && speed > 1.0E-6) {
            Vec3 motion = toDest.scale(Math.min(speed, dist) / dist);
            move(MoverType.SELF, motion);
            if (this.horizontalCollision || this.verticalCollision) {
                onBlockCollision(level, route, collisionNormal(motion));
                return;
            }
        }

        if (target == null && center().distanceToSqr(dest) < 1.0E-4) {
            targetIndex = (targetIndex + 1) % route.waypoints().size();
        }

        List<LivingEntity> touched = level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(CONTACT_MARGIN), AnomalyCombat::isValidTeslaTarget);
        if (!touched.isEmpty()) {
            shock(level, route, touched);
        }
    }

    /** Where to fly while chasing: straight at the target. Gravi keeps to its leash. */
    protected Vec3 chaseDestination(TeslaRoute route, Player target) {
        return target.getBoundingBox().getCenter();
    }

    /** Is {@code target} the one being chased right now (server side)? */
    protected boolean isChasing(Entity target) {
        return getState() == State.CHASE && target.getUUID().equals(chaseTargetUuid);
    }

    private void updateChaseTarget(ServerLevel level, TeslaRoute route) {
        double radius = route.chaseRadius();
        Vec3 c = center();

        if (getState() == State.CHASE) {
            Player current = resolveTarget(level);
            if (current != null && isChaseable(current, c, radius)) return;
            // Target left the radius, dropped the flag, died, logged out or changed dimension.
            chaseTargetUuid = null;
            targetIndex = nearestWaypointIndex(route);
            setState(State.PATROL);
        }

        if (radius <= 0 || this.tickCount % CHASE_SCAN_INTERVAL != 0) return;

        ServerPlayer best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!isChaseable(player, c, radius)) continue;
            double d = player.getBoundingBox().getCenter().distanceToSqr(c);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = player;
            }
        }
        if (best != null) {
            chaseTargetUuid = best.getUUID();
            setState(State.CHASE);
        }
    }

    private static boolean isChaseable(Player player, Vec3 from, double radius) {
        return AnomalyCombat.isValidTeslaTarget(player)
                && hasArtifactFlag(player)
                && player.getBoundingBox().getCenter().distanceToSqr(from) <= radius * radius;
    }

    @Nullable
    private Player resolveTarget(ServerLevel level) {
        return chaseTargetUuid == null ? null : level.getPlayerByUUID(chaseTargetUuid);
    }

    private int nearestWaypointIndex(TeslaRoute route) {
        Vec3 c = center();
        int best = 0;
        double bestDistSq = Double.MAX_VALUE;
        List<BlockPos> points = route.waypoints();
        for (int i = 0; i < points.size(); i++) {
            double d = TeslaGeometry.center(points.get(i)).distanceToSqr(c);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = i;
            }
        }
        return best;
    }

    /** Which way the wall faces: the opposite of the blocked component of the attempted motion. */
    private Vec3 collisionNormal(Vec3 motion) {
        if (this.verticalCollision && Math.abs(motion.y) > 1.0E-6) {
            return new Vec3(0, -Math.signum(motion.y), 0);
        }
        if (Math.abs(motion.x) >= Math.abs(motion.z)) {
            return new Vec3(-Math.signum(motion.x), 0, 0);
        }
        return new Vec3(0, 0, -Math.signum(motion.z));
    }

    /** Flew into a block. The Tesla pops (bolts scatter away from the wall); the Comet overrides this. */
    protected void onBlockCollision(ServerLevel level, TeslaRoute route, Vec3 normal) {
        pop(level, route, normal, false);
    }

    /** One hit on contact; the electrification that follows is purely visual. The Comet overrides this. */
    protected void shock(ServerLevel level, TeslaRoute route, List<LivingEntity> touched) {
        for (LivingEntity target : touched) {
            AnomalyCombat.hurt(level, target, Tesla.DAMAGE_TYPE, route.damage(), position());
            AnomalyCombat.playRandom(level, target.getBoundingBox().getCenter(), Tesla.HIT_SOUNDS, Tesla.SOUND_VOLUME);
            ElectrifyPacket.send(target, ModCommonConfig.electrifyTicks(), 0, route.intensity());
        }
        pop(level, route, null, true);
    }

    /** @param normal the struck wall's normal, or {@code null} for a contact pop (bolts in all directions) */
    private void pop(ServerLevel level, TeslaRoute route, @Nullable Vec3 normal, boolean contact) {
        Vec3 c = center();
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
                new TeslaBurstPacket(c, normal, getSize(), route.intensity()));
        AnomalyCombat.playSound(level, c, contact ? Tesla.CONTACT_SOUND : Tesla.BLOCK_SOUND, Tesla.SOUND_VOLUME);
        die();
    }

    /** Gone until the respawn delay passes (then it reappears on a random waypoint). */
    protected void die() {
        chaseTargetUuid = null;
        setState(State.DEAD);
    }

    private void respawn(TeslaRoute route) {
        List<BlockPos> points = route.waypoints();
        int index = this.random.nextInt(points.size());
        setCenter(TeslaGeometry.center(points.get(index)));
        targetIndex = (index + 1) % points.size();
        setState(State.SPAWNING);
    }

    // ---- untouchable -------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    /** No step sounds and no vibrations for sculk sensors from its movement. */
    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    /** Only its own movement moves it — pistons, shulkers and players can't shove it around. */
    @Override
    public void move(MoverType type, Vec3 motion) {
        if (type == MoverType.SELF) {
            super.move(type, motion);
        }
    }

    // ---- persistence -------------------------------------------------------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.routeId = tag.getInt("route");
        this.targetIndex = tag.getInt("target");
        this.stateTicks = tag.getInt("state_ticks");
        int ordinal = tag.getByte("state");
        State state = ordinal >= 0 && ordinal < State.values().length ? State.values()[ordinal] : State.PATROL;
        if (state == State.CHASE) state = State.PATROL;
        this.entityData.set(DATA_STATE, (byte) state.ordinal());
        if (tag.contains("size")) this.entityData.set(DATA_SIZE, tag.getFloat("size"));
        if (tag.contains("intensity")) this.entityData.set(DATA_INTENSITY, tag.getInt("intensity"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("route", routeId);
        tag.putInt("target", targetIndex);
        tag.putInt("state_ticks", stateTicks);
        tag.putByte("state", (byte) getState().ordinal());
        tag.putFloat("size", getSize());
        tag.putInt("intensity", getIntensity());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
