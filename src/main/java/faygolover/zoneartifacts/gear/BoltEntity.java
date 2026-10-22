package faygolover.zoneartifacts.gear;

import faygolover.zoneartifacts.registry.ModEntities;
import faygolover.zoneartifacts.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown bolt or nut. It flies like a snowball (a shorter throw), hits nobody hard (no damage, no
 * knock — only its sound), bounces a couple of times, each lower, and comes to rest as the item
 * again — four times in five; otherwise it's lost. Only the first touch sounds (the bounces are in
 * the sound): of what it struck ({@link BoltSounds}), or a splash when it drops into water (a throw
 * made under water makes none). Anomalies treat it as any thrown thing (it is a projectile).
 */
public class BoltEntity extends ThrowableItemProjectile {

    private static final int MAX_BOUNCES = 3;
    private static final double REST_SPEED = 0.09;
    /** Chance to be found again where it came to rest. */
    private static final float KEPT = 0.8f;

    private int bounces;
    private boolean sounded;

    public BoltEntity(EntityType<? extends BoltEntity> type, Level level) {
        super(type, level);
    }

    public BoltEntity(Level level, LivingEntity thrower) {
        super(ModEntities.BOLT.get(), thrower, level);
        // Thrown under water: it's already in, no splash.
        this.sounded = thrower.isEyeInFluid(FluidTags.WATER);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BOLT.get();
    }

    /** Client: how far it has turned over (degrees), now and a tick ago — fast in flight, lazily
     *  when it hangs slowly drifting (a Lift). */
    public float spin;
    public float spinO;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spinO = spin;
            spin += Mth.clamp((float) getDeltaMovement().length() * 60.0f, 1.2f, 28.0f);
        }
        if (!level().isClientSide && !sounded && isInWater()) {
            sounded = true;
            sound(BoltSounds.Group.WATER);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        // No damage and no push: it glances off with its sound. (The client bounces it the same way,
        // or it would fly on into whatever it hit and vanish there until the server corrects it.)
        if (!level().isClientSide) touch(BoltSounds.Group.DEFAULT);
        setDeltaMovement(getDeltaMovement().scale(-0.2));
        bounces++;
        settleIfSpent();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) touch(BoltSounds.group(level().getBlockState(hit.getBlockPos())));
        // Bounce (on both sides alike): the part into the wall turned back and mostly lost, the rest slowed.
        Direction face = hit.getDirection();
        Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 v = getDeltaMovement();
        double into = v.dot(n);
        Vec3 along = v.subtract(n.scale(into));
        setDeltaMovement(along.scale(0.55).add(n.scale(-into * 0.35)));
        Vec3 at = hit.getLocation().add(n.scale(0.06));
        setPos(at.x, at.y, at.z);
        bounces++;
        settleIfSpent();
    }

    private void touch(BoltSounds.Group group) {
        if (sounded) return;
        sounded = true;
        sound(group);
    }

    private void sound(BoltSounds.Group group) {
        level().playSound(null, getX(), getY(), getZ(), group.sound(), SoundSource.NEUTRAL, 1.0f, 0.92f + random.nextFloat() * 0.16f);
    }

    /** Bounced out: at rest — the item again (or lost). The client only holds it still until the server's word. */
    private void settleIfSpent() {
        if (bounces < MAX_BOUNCES && getDeltaMovement().length() > REST_SPEED) return;
        if (level().isClientSide) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (level() instanceof ServerLevel server && random.nextFloat() < KEPT) {
            ItemEntity item = new ItemEntity(server, getX(), getY(), getZ(), getItem().copyWithCount(1));
            item.setDeltaMovement(getDeltaMovement().multiply(0.3, 0.0, 0.3));
            item.setDefaultPickUpDelay();
            server.addFreshEntity(item);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("bounces", bounces);
        tag.putBoolean("sounded", sounded);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        bounces = tag.getInt("bounces");
        sounded = tag.getBoolean("sounded");
    }
}
