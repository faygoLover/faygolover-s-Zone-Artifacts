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
    private BlockPos pos;
    private double size;
    /** Width (x), height (y), length (z) in blocks where the zone may be a box ({@link AnomalyDefaults#boxShaped});
     *  0 = a cube of {@link #size}. */
    private double sizeX;
    private double sizeY;
    private double sizeZ;
    /** The KPK's switches: working at all, hurting, heard, seen. */
    private boolean enabled = true;
    private boolean harmful = true;
    private boolean audible = true;
    private boolean visible = true;
    /** Acts on players in creative / spectator mode too (the KPK's fifth switch; off by default). */
    private boolean targetsGm;
    private double cooldownSeconds;
    private float damage;
    private int intensity;
    /** Force multiplier of the gravitational anomalies (Plesh: throw, Voronka/Karusel: pull,
     *  Podushka: launch height). 1.0 for everything else. */
    private double speed = 1.0;
    /** The targeting tuner's value where a zone anomaly uses it (Khlopushka: how far its flash
     *  blinds; Firefly: how high it flies). */
    private double range;
    /** Which way the GM faced when placing it (Phantom Light: which way its rows face), degrees. */
    private float yaw;

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
        AnomalyInstance instance = new AnomalyInstance(typeId, pos.immutable(), AnomalyDefaults.size(typeId),
                AnomalyDefaults.cooldownSeconds(typeId), AnomalyDefaults.damage(typeId), AnomalyDefaults.intensity(typeId));
        instance.range = AnomalyDefaults.range(typeId);
        return instance;
    }

    public ResourceLocation typeId() {
        return typeId;
    }

    public BlockPos pos() {
        return pos;
    }

    /** Moved with the KPK. */
    public void setPos(BlockPos pos) {
        this.pos = pos.immutable();
    }

    public double sizeX() {
        return sizeX > 0 ? sizeX : size;
    }

    public double sizeY() {
        return sizeY > 0 ? sizeY : size;
    }

    public double sizeZ() {
        return sizeZ > 0 ? sizeZ : size;
    }

    /** A box of its own proportions (the largest side stands in as its "size" wherever one number is needed). */
    public void setDimensions(double x, double y, double z) {
        this.sizeX = x;
        this.sizeY = y;
        this.sizeZ = z;
        this.size = Math.max(x, Math.max(y, z));
    }

    /** Back to a cube of its size. */
    public void clearDimensions() {
        this.sizeX = 0;
        this.sizeY = 0;
        this.sizeZ = 0;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean harmful() {
        return harmful;
    }

    public boolean audible() {
        return audible;
    }

    public boolean visible() {
        return visible;
    }

    public boolean targetsGm() {
        return targetsGm;
    }

    /** From the packed bits ({@link #switchBits}). */
    public void setSwitchBits(int bits) {
        this.enabled = (bits & 1) != 0;
        this.harmful = (bits & 2) != 0;
        this.audible = (bits & 4) != 0;
        this.visible = (bits & 8) != 0;
        this.targetsGm = (bits & 16) != 0;
    }

    /** The switches packed: bit 0 enabled, 1 harmful, 2 audible, 3 visible, 4 acts on creative / spectators. */
    public int switchBits() {
        return (enabled ? 1 : 0) | (harmful ? 2 : 0) | (audible ? 4 : 0) | (visible ? 8 : 0) | (targetsGm ? 16 : 0);
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

    public double range() {
        return range;
    }

    public void setRange(double range) {
        this.range = range;
    }

    public float yaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
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
        tag.putDouble("range", range);
        tag.putFloat("yaw", yaw);
        if (sizeX > 0) {
            tag.putDouble("size_x", sizeX);
            tag.putDouble("size_y", sizeY);
            tag.putDouble("size_z", sizeZ);
        }
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("harmful", harmful);
        tag.putBoolean("audible", audible);
        tag.putBoolean("visible", visible);
        tag.putBoolean("targets_gm", targetsGm);
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
        instance.range = tag.contains("range") ? tag.getDouble("range") : AnomalyDefaults.range(typeId);
        if (tag.contains("yaw")) instance.yaw = tag.getFloat("yaw");
        if (tag.contains("size_x")) {
            instance.sizeX = tag.getDouble("size_x");
            instance.sizeY = tag.getDouble("size_y");
            instance.sizeZ = tag.getDouble("size_z");
        }
        if (tag.contains("enabled")) instance.enabled = tag.getBoolean("enabled");
        if (tag.contains("harmful")) instance.harmful = tag.getBoolean("harmful");
        if (tag.contains("audible")) instance.audible = tag.getBoolean("audible");
        if (tag.contains("visible")) instance.visible = tag.getBoolean("visible");
        instance.targetsGm = tag.getBoolean("targets_gm");
        return instance;
    }
}
