package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One zone anomaly placed in the world, with its own settings (changed with the tuners):
 * size in blocks, cooldown in seconds, damage per hit and visual intensity. The cooldown timer
 * itself is runtime-only and restarts on world load.
 */
public class AnomalyInstance {

    private final ResourceLocation typeId;
    private final BlockPos pos;
    private double size;
    private int cooldownSeconds;
    private float damage;
    private int intensity;

    private transient int cooldownTicks;

    public AnomalyInstance(ResourceLocation typeId, BlockPos pos, double size, int cooldownSeconds, float damage, int intensity) {
        this.typeId = typeId;
        this.pos = pos;
        this.size = size;
        this.cooldownSeconds = cooldownSeconds;
        this.damage = damage;
        this.intensity = intensity;
    }

    /** A new Electra with the defaults from the common config. */
    public static AnomalyInstance newElectra(BlockPos pos) {
        return new AnomalyInstance(AnomalyTypeIds.ELECTRA, pos.immutable(), Electra.DEFAULT_SIZE,
                ModCommonConfig.ELECTRA_COOLDOWN_SECONDS.get(),
                ModCommonConfig.ELECTRA_DAMAGE.get().floatValue(),
                ModCommonConfig.ELECTRA_INTENSITY.get());
    }

    public ResourceLocation typeId() {
        return typeId;
    }

    public BlockPos pos() {
        return pos;
    }

    public double size() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int seconds) {
        this.cooldownSeconds = seconds;
    }

    public float damage() {
        return damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public int intensity() {
        return intensity;
    }

    public void setIntensity(int intensity) {
        this.intensity = intensity;
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
        tag.putDouble("size", size);
        tag.putInt("cooldown_seconds", cooldownSeconds);
        tag.putFloat("damage", damage);
        tag.putInt("intensity", intensity);
        return tag;
    }

    public static AnomalyInstance load(CompoundTag tag) {
        ResourceLocation typeId = new ResourceLocation(tag.getString("type"));
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));

        // Anomalies saved before 0.1.3.0 only had a level 1..3, whose sizes were 1/2/3 blocks,
        // and took everything else from the (now removed) datapack — so fill in today's defaults.
        double size = tag.contains("size") ? tag.getDouble("size") : Math.max(1, tag.getInt("level"));
        int cooldown = tag.contains("cooldown_seconds") ? tag.getInt("cooldown_seconds") : ModCommonConfig.ELECTRA_COOLDOWN_SECONDS.get();
        float damage = tag.contains("damage") ? tag.getFloat("damage") : ModCommonConfig.ELECTRA_DAMAGE.get().floatValue();
        int intensity = tag.contains("intensity") ? tag.getInt("intensity") : ModCommonConfig.ELECTRA_INTENSITY.get();
        return new AnomalyInstance(typeId, pos, size, cooldown, damage, intensity);
    }
}
