package faygolover.zoneartifacts.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One soap bubble ({@link BubbleEngine}): a gravitational knot drifting about its zone, pushing off
 * the other bubbles and off blocks. When anything touches it (not merely comes near) — or it is hit
 * by anything, a snowball included — it charges for {@link BubbleEngine#CHARGE_TICKS} ticks and
 * bursts. Never saved: its zone makes new ones.
 */
public class BubbleEntity extends Entity {

    /** Ticks left before it bursts, -1 = calm. */
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(BubbleEntity.class, EntityDataSerializers.INT);

    @Nullable
    private BlockPos zonePos;
    private Vec3 wander = Vec3.ZERO;

    public BubbleEntity(EntityType<? extends BubbleEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CHARGE, -1);
    }

    public void setZone(BlockPos pos) {
        this.zonePos = pos.immutable();
    }

    @Nullable
    public BlockPos zone() {
        return zonePos;
    }

    public int charge() {
        return this.entityData.get(CHARGE);
    }

    public boolean charging() {
        return charge() >= 0;
    }

    /** Starts the charge (or shortens it), unless it's already closer to bursting. */
    public void trigger(int ticks) {
        int c = charge();
        if (c < 0 || c > ticks) this.entityData.set(CHARGE, ticks);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) return;
        AnomalyInstance instance = BubbleEngine.instanceFor(level, zonePos);
        if (instance == null) {
            discard();
            return;
        }
        int c = charge();
        if (c >= 0) {
            if (c == 0) {
                BubbleEngine.burst(level, this, instance);
                return;
            }
            this.entityData.set(CHARGE, c - 1);
            // Shivering in place while it charges.
            setDeltaMovement(getDeltaMovement().scale(0.5));
        } else {
            drift(level, instance);
            if (touched(level)) trigger(BubbleEngine.CHARGE_TICKS);
        }
        move(MoverType.SELF, getDeltaMovement());
        Vec3 v = getDeltaMovement();
        // Bounce off blocks.
        if (horizontalCollision) v = new Vec3(-v.x * 0.8, v.y, -v.z * 0.8);
        if (verticalCollision) v = new Vec3(v.x, -v.y * 0.8, v.z);
        setDeltaMovement(v);
    }

    /** Wandering, pushed off its neighbours, kept inside its zone. */
    private void drift(ServerLevel level, AnomalyInstance instance) {
        double speed = 0.03 * Math.max(0.1, instance.speed());
        if (random.nextInt(20) == 0 || wander.lengthSqr() < 1.0E-6) {
            wander = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5, random.nextGaussian()).normalize().scale(speed);
        }
        Vec3 v = getDeltaMovement().scale(0.92).add(wander.scale(0.08));
        for (BubbleEntity other : level.getEntitiesOfClass(BubbleEntity.class, getBoundingBox().inflate(1.2), b -> b != this)) {
            Vec3 away = position().subtract(other.position());
            double d = away.length();
            if (d < 1.0E-3) away = new Vec3(random.nextGaussian(), 0.0, random.nextGaussian());
            v = v.add(away.normalize().scale(0.012 * (1.8 - Math.min(1.8, d))));
        }
        AABB zone = AnomalyGeometry.zoneAabb(instance).deflate(0.4);
        Vec3 p = position().add(0.0, getBbHeight() / 2.0, 0.0);
        if (!zone.contains(p)) v = v.add(zone.getCenter().subtract(p).normalize().scale(0.01));
        double len = v.length();
        if (len > speed * 1.5) v = v.scale(speed * 1.5 / len);
        setDeltaMovement(v);
    }

    /** Something (other than a bubble) actually touches it. */
    private boolean touched(ServerLevel level) {
        List<Entity> near = level.getEntities(this, getBoundingBox(), e -> !(e instanceof BubbleEntity) && !e.isSpectator() && e.isAlive());
        return !near.isEmpty();
    }

    /** Any hit — a fist, an arrow, a snowball — sets it off. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !isRemoved()) trigger(BubbleEngine.CHARGE_TICKS);
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
