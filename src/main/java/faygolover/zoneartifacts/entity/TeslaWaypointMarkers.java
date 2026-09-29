package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Keeps {@link TeslaWaypointEntity} markers in the world in sync with what {@code
 * TeslaRouteInteractionHandler} does to a chain or route: one marker per point, spawned the instant
 * a point is added and discarded the instant it's removed. A point that survives a chain closing
 * into a real {@link TeslaRoute} keeps its existing marker untouched - only additions and removals
 * ever call here.
 */
public final class TeslaWaypointMarkers {

    private TeslaWaypointMarkers() {
    }

    public static void spawn(ServerLevel level, BlockPos pos) {
        TeslaWaypointEntity marker = new TeslaWaypointEntity(ModEntities.TESLA_WAYPOINT.get(), level);
        marker.setWaypointPos(pos);
        level.addFreshEntity(marker);
    }

    public static void discardAt(ServerLevel level, BlockPos pos) {
        for (Entity entity : level.getEntities((Entity) null, new AABB(pos), e -> true)) {
            if (entity instanceof TeslaWaypointEntity marker && marker.waypointPos().equals(pos)) {
                marker.discard();
            }
        }
    }

    public static void discardAll(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            discardAt(level, pos);
        }
    }
}
