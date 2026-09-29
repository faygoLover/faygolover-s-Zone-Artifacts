package faygolover.zoneartifacts.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One zone anomaly placed in the world, with its own settings changed with the tuners: size in
 * blocks, cooldown in seconds (for Zharka and Iney: the damage interval), damage per hit, visual
 * intensity and — for the gravitational ones — force ({@link #speed()}, x1.0 = standard).
 * Timers and the "someone is inside" flag are runtime-only.
 */
public class AnomalyInstance {

    private final ResourceLocation typeId;
    private final BlockPos pos;
    private double size;
    private double cooldownSeconds;
    private float damage;
    private int intensity;
    /** Force multiplier of the gravitational anomalies (Plesh: throw, Voronka/Karusel: pull,
     *  Podushka: launch height). 1.0 for everything else. */
    private double speed = 1.0;

    private transient int cooldownTicks;
    /** Gravity: random seed of the current phase (orbits, throw directions), sent to clients. */
    private transient int phaseSeed;

    // Thermal (passive-field) runtime state, not saved.
    private transient boolean active;
    private transient int pulseTicks;
    private transient int blockTicks;

    // Razlom runtime state, not saved: the entity its fire jet is aimed at (-1 = none).
    private transient int jetTargetId = -1;
    // Where the jet's end is, how it is moving, and its wandering heading once it has no target.
    private transient Vec3 jetAim = Vec3.ZERO;
    private transient Vec3 jetVelocity = Vec3.ZERO;
    private transient double jetWander;

    public AnomalyInstance(ResourceLocation typeId, BlockPos pos, double size, double cooldownSeconds, float damage, int intensity) {
        this.typeId = typeId;
        this.pos = pos;
        this.size = size;
        this.cooldownSeconds = cooldownSeconds;
        this.damage = damage;
        this.intensity = intensity;
    }

    /** A new anomaly of {@code typeId} with that type's defaults from the common config. */
    public static AnomalyInstance create(ResourceLocation typeId, BlockPos pos) {
        return new AnomalyInstance(typeId, pos.immutable(), AnomalyDefaults.SIZE,
                AnomalyDefaults.cooldownSeconds(typeId), AnomalyDefaults.damage(typeId), AnomalyDefaults.intensity(typeId));
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

    /** Seconds; Electra keeps whole seconds, Zharka / Iney allow tenths (down to 0.1). */
    public double cooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(double seconds) {
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

    /** Thermal anomalies: someone is inside right now (synced to clients for the visuals). */
    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int pulseTicks() {
        return pulseTicks;
    }

    public void setPulseTicks(int ticks) {
        this.pulseTicks = ticks;
    }

    public int blockTicks() {
        return blockTicks;
    }

    public void setBlockTicks(int ticks) {
        this.blockTicks = ticks;
    }

    public int jetTargetId() {
        return jetTargetId;
    }

    public void setJetTargetId(int id) {
        this.jetTargetId = id;
    }

    public Vec3 jetAim() {
        return jetAim;
    }

    public void setJetAim(Vec3 aim) {
        this.jetAim = aim;
    }

    public Vec3 jetVelocity() {
        return jetVelocity;
    }

    public void setJetVelocity(Vec3 velocity) {
        this.jetVelocity = velocity;
    }

    public double jetWander() {
        return jetWander;
    }

    public void setJetWander(double wander) {
        this.jetWander = wander;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public int phaseSeed() {
        return phaseSeed;
    }

    public void setPhaseSeed(int seed) {
        this.phaseSeed = seed;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", typeId.toString());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putDouble("size", size);
        tag.putDouble("cooldown_seconds", cooldownSeconds);
        tag.putDouble("speed", speed);
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
        double cooldown = tag.contains("cooldown_seconds") ? tag.getDouble("cooldown_seconds") : AnomalyDefaults.cooldownSeconds(typeId);
        float damage = tag.contains("damage") ? tag.getFloat("damage") : AnomalyDefaults.damage(typeId);
        int intensity = tag.contains("intensity") ? tag.getInt("intensity") : AnomalyDefaults.intensity(typeId);
        AnomalyInstance instance = new AnomalyInstance(typeId, pos, size, cooldown, damage, intensity);
        if (tag.contains("speed")) instance.speed = tag.getDouble("speed");
        return instance;
    }
}
