package faygolover.zoneartifacts.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A completed route: a closed loop of waypoints, the anomaly flying it ({@link RouteKind}: a Tesla
 * or a Comet — the fields keep the "tesla" names from before the Comet existed), and that anomaly's
 * settings (changed with the tuners). Settings live on the route, not the entity, so they survive
 * the Tesla popping and respawning; the entity reads them every tick, so a change applies at once
 * (the respawn delay at the next pop).
 * <p>
 * {@code lastKnownTeslaPos} is where the Tesla was last seen ticking. The cleanup command uses it
 * to tell "the Tesla is really gone" from "it just sits in an unloaded chunk right now".
 */
public final class TeslaRoute {

    private final int id;
    private final RouteKind kind;
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
    private double chaseRadius;

    /** A new route with its kind's defaults from the common config. */
    public TeslaRoute(int id, RouteKind kind, List<BlockPos> waypoints) {
        this.id = id;
        this.kind = kind;
        this.waypoints = List.copyOf(waypoints);
        this.respawnSeconds = kind.defaultRespawnSeconds();
        this.damage = kind.defaultDamage();
        this.intensity = kind.defaultIntensity();
        this.chaseRadius = kind.defaultChaseRadius();
    }

    public int id() {
        return id;
    }

    public RouteKind kind() {
        return kind;
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

    /** Targeting distance: how close a flagged player must be for the Tesla to chase them. 0 = never. */
    public double chaseRadius() {
        return chaseRadius;
    }

    public void setChaseRadius(double radius) {
        this.chaseRadius = radius;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("kind", kind.id());
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
        tag.putDouble("chase_radius", chaseRadius);
        return tag;
    }

    /** Routes saved by 0.1.2.0 have no settings yet — they get today's defaults. */
    public static TeslaRoute load(CompoundTag tag) {
        long[] packed = tag.getLongArray("waypoints");
        List<BlockPos> points = new ArrayList<>(packed.length);
        for (long p : packed) {
            points.add(BlockPos.of(p));
        }
        RouteKind kind = tag.contains("kind") ? RouteKind.byId(tag.getString("kind")) : RouteKind.TESLA;
        TeslaRoute route = new TeslaRoute(tag.getInt("id"), kind, points);
        if (tag.hasUUID("tesla")) route.teslaUuid = tag.getUUID("tesla");
        if (tag.contains("last_pos")) route.lastKnownTeslaPos = BlockPos.of(tag.getLong("last_pos"));
        if (tag.contains("size")) route.size = tag.getDouble("size");
        if (tag.contains("speed")) route.speedMultiplier = tag.getDouble("speed");
        if (tag.contains("respawn_seconds")) route.respawnSeconds = tag.getInt("respawn_seconds");
        if (tag.contains("damage")) route.damage = tag.getFloat("damage");
        if (tag.contains("intensity")) route.intensity = tag.getInt("intensity");
        if (tag.contains("chase_radius")) route.chaseRadius = tag.getDouble("chase_radius");
        return route;
    }
}
