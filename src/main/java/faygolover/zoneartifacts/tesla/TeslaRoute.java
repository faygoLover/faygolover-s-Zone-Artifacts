package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.config.ModCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A completed Tesla route: a closed loop of waypoints, the Tesla living on it, and that Tesla's
 * settings (changed with the tuners). Settings live on the route, not the entity, so they survive
 * the Tesla popping and respawning; the entity reads them every tick, so a change applies at once
 * (the respawn delay at the next pop).
 * <p>
 * {@code lastKnownTeslaPos} is where the Tesla was last seen ticking. The cleanup command uses it
 * to tell "the Tesla is really gone" from "it just sits in an unloaded chunk right now".
 */
public final class TeslaRoute {

    private final int id;
    private final List<BlockPos> waypoints;
    @Nullable
    private UUID teslaUuid;
    @Nullable
    private BlockPos lastKnownTeslaPos;

    private double size = Tesla.DEFAULT_SIZE;
    private double speedMultiplier = 1.0;
    private int respawnSeconds;
    private float damage;
    private int intensity;

    /** A new route with the defaults from the common config. */
    public TeslaRoute(int id, List<BlockPos> waypoints) {
        this.id = id;
        this.waypoints = List.copyOf(waypoints);
        this.respawnSeconds = ModCommonConfig.TESLA_RESPAWN_SECONDS.get();
        this.damage = ModCommonConfig.TESLA_DAMAGE.get().floatValue();
        this.intensity = ModCommonConfig.TESLA_INTENSITY.get();
    }

    public int id() {
        return id;
    }

    public List<BlockPos> waypoints() {
        return waypoints;
    }

    @Nullable
    public UUID teslaUuid() {
        return teslaUuid;
    }

    public void setTeslaUuid(@Nullable UUID uuid) {
        this.teslaUuid = uuid;
    }

    @Nullable
    public BlockPos lastKnownTeslaPos() {
        return lastKnownTeslaPos;
    }

    public void setLastKnownTeslaPos(@Nullable BlockPos pos) {
        this.lastKnownTeslaPos = pos;
    }

    public double size() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public double speedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(double multiplier) {
        this.speedMultiplier = multiplier;
    }

    public int respawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(int seconds) {
        this.respawnSeconds = seconds;
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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        long[] packed = new long[waypoints.size()];
        for (int i = 0; i < waypoints.size(); i++) {
            packed[i] = waypoints.get(i).asLong();
        }
        tag.putLongArray("waypoints", packed);
        if (teslaUuid != null) tag.putUUID("tesla", teslaUuid);
        if (lastKnownTeslaPos != null) tag.putLong("last_pos", lastKnownTeslaPos.asLong());
        tag.putDouble("size", size);
        tag.putDouble("speed", speedMultiplier);
        tag.putInt("respawn_seconds", respawnSeconds);
        tag.putFloat("damage", damage);
        tag.putInt("intensity", intensity);
        return tag;
    }

    /** Routes saved by 0.1.2.0 have no settings yet — they get today's defaults. */
    public static TeslaRoute load(CompoundTag tag) {
        long[] packed = tag.getLongArray("waypoints");
        List<BlockPos> points = new ArrayList<>(packed.length);
        for (long p : packed) {
            points.add(BlockPos.of(p));
        }
        TeslaRoute route = new TeslaRoute(tag.getInt("id"), points);
        if (tag.hasUUID("tesla")) route.teslaUuid = tag.getUUID("tesla");
        if (tag.contains("last_pos")) route.lastKnownTeslaPos = BlockPos.of(tag.getLong("last_pos"));
        if (tag.contains("size")) route.size = tag.getDouble("size");
        if (tag.contains("speed")) route.speedMultiplier = tag.getDouble("speed");
        if (tag.contains("respawn_seconds")) route.respawnSeconds = tag.getInt("respawn_seconds");
        if (tag.contains("damage")) route.damage = tag.getFloat("damage");
        if (tag.contains("intensity")) route.intensity = tag.getInt("intensity");
        return route;
    }
}
