package faygolover.zoneartifacts.block;

import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The Hedgehog's settings (the tuners): the patch's radius, how many spikes, how fast they move. */
public class EzhikBlockEntity extends BlockEntity {

    public static final double MIN_RADIUS = 0.3;
    public static final double MAX_RADIUS = 4.0;

    private double radius;
    private int intensity;
    private double speed = 1.0;

    public EzhikBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EZHIK.get(), pos, state);
        this.radius = ModCommonConfig.EZHIK_SIZE.get();
        this.intensity = ModCommonConfig.EZHIK_INTENSITY.get();
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(EzhikBlock.FACING) ? state.getValue(EzhikBlock.FACING) : Direction.UP;
    }

    public double radius() {
        return radius;
    }

    public int intensity() {
        return intensity;
    }

    public double speed() {
        return speed;
    }

    public void setRadius(double radius) {
        this.radius = Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        changed();
    }

    public void setIntensity(int intensity) {
        this.intensity = Math.max(1, intensity);
        changed();
    }

    public void setSpeed(double speed) {
        this.speed = Mth.clamp(speed, 0.0, 10.0);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("radius")) radius = tag.getDouble("radius");
        if (tag.contains("intensity")) intensity = tag.getInt("intensity");
        if (tag.contains("speed")) speed = tag.getDouble("speed");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("radius", radius);
        tag.putInt("intensity", intensity);
        tag.putDouble("speed", speed);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(radius + 1.0);
    }
}
