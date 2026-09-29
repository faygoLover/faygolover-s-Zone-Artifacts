package faygolover.zoneartifacts.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A closed loop of waypoints a Tesla patrols, set up by a GM with the waypoint tool. Closed means
 * exactly that: after the last waypoint she heads back to {@code waypoints.get(0)}, no special
 * "end" handling needed. Persisted in {@link TeslaSavedData}, independent of whatever {@link
 * TeslaEntity} instance is currently alive for it (or isn't, mid-respawn) — the route is the
 * durable thing; the entity comes and goes. {@code typeId} is carried on the route rather than
 * only on the live entity, so a respawn (which starts from a route, not a dying entity) still
 * knows which {@link TeslaType} to spawn.
 */
public final class TeslaRoute {

    private final int id;
    private final ResourceLocation typeId;
    private final List<BlockPos> waypoints;

    public TeslaRoute(int id, ResourceLocation typeId, List<BlockPos> waypoints) {
        this.id = id;
        this.typeId = typeId;
        this.waypoints = List.copyOf(waypoints);
    }

    public int id() {
        return id;
    }

    public ResourceLocation typeId() {
        return typeId;
    }

    public List<BlockPos> waypoints() {
        return waypoints;
    }

    public boolean contains(BlockPos pos) {
        return waypoints.contains(pos);
    }
}
