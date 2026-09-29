package faygolover.zoneartifacts.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A completed Tesla route: a closed loop of waypoints plus the Tesla living on it.
 * <p>
 * {@code lastKnownTeslaPos} is where the Tesla was last seen ticking. The cleanup command uses it
 * to tell "the Tesla is really gone" (its chunk is loaded, yet no entity) from "the Tesla just
 * sits in an unloaded chunk right now" — the latter must never be deleted.
 */
public final class TeslaRoute {

    private final int id;
    private final List<BlockPos> waypoints;
    @Nullable
    private UUID teslaUuid;
    @Nullable
    private BlockPos lastKnownTeslaPos;

    public TeslaRoute(int id, List<BlockPos> waypoints) {
        this.id = id;
        this.waypoints = List.copyOf(waypoints);
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
        return tag;
    }

    public static TeslaRoute load(CompoundTag tag) {
        long[] packed = tag.getLongArray("waypoints");
        List<BlockPos> points = new ArrayList<>(packed.length);
        for (long p : packed) {
            points.add(BlockPos.of(p));
        }
        TeslaRoute route = new TeslaRoute(tag.getInt("id"), points);
        if (tag.hasUUID("tesla")) route.teslaUuid = tag.getUUID("tesla");
        if (tag.contains("last_pos")) route.lastKnownTeslaPos = BlockPos.of(tag.getLong("last_pos"));
        return route;
    }
}
