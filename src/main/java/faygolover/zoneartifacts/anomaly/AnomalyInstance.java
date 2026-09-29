package faygolover.zoneartifacts.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One anomaly placed in the world: its type, position and level are persisted;
 * the cooldown timer is runtime-only and simply restarts (at 0, i.e. "ready") on world (re)load.
 */
public class AnomalyInstance {

    private final ResourceLocation typeId;
    private final BlockPos pos;
    private int level;

    private transient int cooldownTicks;

    public AnomalyInstance(ResourceLocation typeId, BlockPos pos, int level) {
        this.typeId = typeId;
        this.pos = pos;
        this.level = level;
    }

    public ResourceLocation typeId() {
        return typeId;
    }

    public BlockPos pos() {
        return pos;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(int ticks) {
        this.cooldownTicks = ticks;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", typeId.toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putInt("level", level);
        return tag;
    }

    public static AnomalyInstance load(CompoundTag tag) {
        ResourceLocation typeId = new ResourceLocation(tag.getString("type"));
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        int level = tag.getInt("level");
        return new AnomalyInstance(typeId, pos, level);
    }
}
