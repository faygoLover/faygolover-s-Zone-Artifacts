package faygolover.zoneartifacts.block;

import faygolover.zoneartifacts.anomaly.AnomalyCombat;
import faygolover.zoneartifacts.anomaly.AnomalyDefaults;
import faygolover.zoneartifacts.anomaly.Gravity;
import faygolover.zoneartifacts.anomaly.Pukh;
import faygolover.zoneartifacts.anomaly.PukhSpores;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.PukhEventPacket;
import faygolover.zoneartifacts.registry.ModBlockEntities;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Burning Fluff's settings and behaviour ({@link Pukh}). Tuned like any anomaly: size = how long
 * the strands hang, cooldown = pause between spore puffs, damage, effects = how dense the fluff
 * is, targeting = how far the puffs reach. Server: burns whoever stands in the strands and shoots
 * puffs at anything that comes near fast. Client: sheds flakes and keeps the strands parted where
 * something pushes through ({@link #parting}), closing slowly behind.
 */
public class PukhBlockEntity extends BlockEntity {

    /** Strand columns per sheet the parting is tracked for (the renderer's columns). */
    public static final int PART_COLUMNS = 5;
    public static final int MAX_SHEETS = 8;

    private double length;
    private float damage;
    private double cooldownSeconds;
    private int intensity;
    private double range;

    private int cooldownTicks;
    private int contactTimer;
    private double cachedLength = -1.0;
    private final Map<Integer, Vec3> lastSeen = new HashMap<>();

    /** Client only: sideways push of each strand column, [sheet][column] = (x, z). */
    public final float[][] partX = new float[MAX_SHEETS][PART_COLUMNS];
    public final float[][] partZ = new float[MAX_SHEETS][PART_COLUMNS];

    /** Client only: the Fluff blocks loaded, for tuner aiming at their strands. */
    public static final java.util.Set<PukhBlockEntity> CLIENT_LOADED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_LOADED.remove(this);
    }

    public PukhBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUKH.get(), pos, state);
        this.length = ModCommonConfig.PUKH_LENGTH.get();
        this.damage = ModCommonConfig.PUKH_DAMAGE.get().floatValue();
        this.cooldownSeconds = ModCommonConfig.PUKH_COOLDOWN_SECONDS.get();
        this.intensity = ModCommonConfig.PUKH_INTENSITY.get();
        this.range = ModCommonConfig.PUKH_RANGE.get();
    }

    public Direction facing() {
        return getBlockState().hasProperty(PukhBlock.FACING) ? getBlockState().getValue(PukhBlock.FACING) : Direction.DOWN;
    }

    public double length() {
        return length;
    }

    public float damage() {
        return damage;
    }

    public double cooldownSeconds() {
        return cooldownSeconds;
    }

    public int intensity() {
        return intensity;
    }

    public double range() {
        return range;
    }

    /** Strand length cut short by what's below (re-checked now and then). */
    public double effectiveLength() {
        if (cachedLength < 0.0 && level != null) cachedLength = Pukh.effectiveLength(level, worldPosition, facing(), length);
        return cachedLength < 0.0 ? length : cachedLength;
    }

    // ---- tuners ------------------------------------------------------------------------------------

    public void setLength(double length) {
        this.length = Mth.clamp(length, Pukh.MIN_LENGTH, Pukh.MAX_LENGTH);
        this.cachedLength = -1.0;
        changed();
    }

    public void setDamage(float damage) {
        this.damage = Math.max(0.0f, damage);
        changed();
    }

    public void setCooldownSeconds(double seconds) {
        this.cooldownSeconds = Math.max(0.5, seconds);
        changed();
    }

    public void setIntensity(int intensity) {
        this.intensity = Math.max(1, intensity);
        changed();
    }

    public void setRange(double range) {
        this.range = Mth.clamp(range, 0.0, Pukh.MAX_RANGE);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ---- ticking ----------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, PukhBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        Direction facing = be.facing();
        if (level.getGameTime() % 40 == 0) be.cachedLength = Pukh.effectiveLength(level, pos, facing, be.length);
        double len = be.effectiveLength();

        // Standing in the strands burns.
        if (++be.contactTimer >= Pukh.CONTACT_INTERVAL) {
            be.contactTimer = 0;
            Vec3 origin = Pukh.puffOrigin(pos, facing, len);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, Pukh.hangingBox(pos, facing, len), PukhBlockEntity::hurtable)) {
                living.invulnerableTime = 0;
                AnomalyCombat.hurt(server, living, Pukh.DAMAGE_TYPE, be.damage, origin);
            }
        }

        // Something coming near fast gets a puff of spores.
        if (be.cooldownTicks > 0) be.cooldownTicks--;
        if (be.range <= 0.0) return;
        Vec3 origin = Pukh.puffOrigin(pos, facing, len);
        List<Entity> near = level.getEntities((Entity) null, new AABB(origin, origin).inflate(be.range), PukhBlockEntity::watched);
        Map<Integer, Vec3> seen = new HashMap<>();
        Entity fastest = null;
        double best = Pukh.FAST;
        for (Entity e : near) {
            Vec3 now = e.position();
            seen.put(e.getId(), now);
            Vec3 before = be.lastSeen.get(e.getId());
            if (before == null || e.getBoundingBox().getCenter().distanceTo(origin) > be.range) continue;
            double speed = now.distanceTo(before);
            if (speed > best && speed < 4.0) {
                best = speed;
                fastest = e;
            }
        }
        be.lastSeen.clear();
        be.lastSeen.putAll(seen);
        if (fastest != null && be.cooldownTicks <= 0) {
            Vec3 at = fastest.getBoundingBox().getCenter();
            PukhSpores.shoot(server, origin, at, be.range + 1.0, be.damage);
            PukhEventPacket.puff(server, origin, at, be.range + 1.0);
            AnomalyCombat.playSound(server, origin, Pukh.PUFF_SOUND, 1.0f, 0.9f + server.random.nextFloat() * 0.2f);
            be.cooldownTicks = AnomalyDefaults.ticks(be.cooldownSeconds);
        }
    }

    private static boolean hurtable(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        return !(entity instanceof Player player && player.isCreative());
    }

    private static boolean watched(Entity entity) {
        return Gravity.movable(entity);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, PukhBlockEntity be) {
        Direction facing = be.facing();
        if (level.getGameTime() % 40 == 0 || be.cachedLength < 0.0) be.cachedLength = Pukh.effectiveLength(level, pos, facing, be.length);
        double len = be.effectiveLength();
        AABB box = Pukh.hangingBox(pos, facing, len);

        // Dark flakes now and then.
        if (level.random.nextInt(Math.max(4, 40 - be.intensity * 4)) == 0) {
            level.addParticle(ModParticles.PUKH_FLAKE.get(), Mth.lerp(level.random.nextDouble(), box.minX, box.maxX),
                    Mth.lerp(level.random.nextDouble(), box.minY, box.maxY), Mth.lerp(level.random.nextDouble(), box.minZ, box.maxZ),
                    0.0, -0.01, 0.0);
        }

        // Strands pushed aside by whatever goes through, closing slowly behind.
        List<Entity> inside = level.getEntities((Entity) null, box.inflate(0.6), e -> e instanceof LivingEntity || e instanceof net.minecraft.world.entity.item.ItemEntity);
        List<PukhLayout.Sheet> sheets = PukhLayout.sheets(facing, be.intensity);
        for (int s = 0; s < sheets.size() && s < MAX_SHEETS; s++) {
            PukhLayout.Sheet sheet = sheets.get(s);
            for (int c = 0; c < PART_COLUMNS; c++) {
                double u = (c + 0.5) / PART_COLUMNS;
                Vec3 local = sheet.origin().add(sheet.across().scale(u * sheet.width()));
                double px = pos.getX() + local.x;
                double pz = pos.getZ() + local.z;
                double tx = 0.0;
                double tz = 0.0;
                for (Entity e : inside) {
                    double dx = px - e.getX();
                    double dz = pz - e.getZ();
                    double d = Math.sqrt(dx * dx + dz * dz);
                    double reach = e.getBbWidth() * 0.5 + 0.45;
                    if (d >= reach) continue;
                    double k = (reach - d) / reach * 0.55;
                    if (d < 1.0E-3) {
                        dx = sheet.across().z;
                        dz = -sheet.across().x;
                        d = 1.0;
                    }
                    tx += dx / d * k;
                    tz += dz / d * k;
                }
                float cx = be.partX[s][c];
                float cz = be.partZ[s][c];
                boolean opening = tx * tx + tz * tz > cx * cx + cz * cz;
                float rate = opening ? 0.45f : 0.04f;
                be.partX[s][c] = cx + ((float) tx - cx) * rate;
                be.partZ[s][c] = cz + ((float) tz - cz) * rate;
            }
        }
    }

    // ---- saving and syncing --------------------------------------------------------------------------

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("length")) length = tag.getDouble("length");
        if (tag.contains("damage")) damage = tag.getFloat("damage");
        if (tag.contains("cooldown")) cooldownSeconds = tag.getDouble("cooldown");
        if (tag.contains("intensity")) intensity = tag.getInt("intensity");
        if (tag.contains("range")) range = tag.getDouble("range");
        cachedLength = -1.0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("length", length);
        tag.putFloat("damage", damage);
        tag.putDouble("cooldown", cooldownSeconds);
        tag.putInt("intensity", intensity);
        tag.putDouble("range", range);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Drawn whenever any of the hanging strands could be in view, not just the base. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).minmax(Pukh.hangingBox(worldPosition, facing(), Math.max(length, 1.0)).inflate(0.6));
    }
}
