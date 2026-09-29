package faygolover.zoneartifacts.client.tesla;

import faygolover.zoneartifacts.network.SyncTeslaRoutesPacket;
import faygolover.zoneartifacts.tesla.RouteKind;
import faygolover.zoneartifacts.tesla.TeslaGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side copy of the Tesla routes in the current dimension. A rendering/click aid only —
 * the server re-validates every action.
 */
public final class TeslaClientCache {

    private static ResourceKey<Level> dimension = null;
    private static List<SyncTeslaRoutesPacket.Entry> routes = List.of();

    private TeslaClientCache() {
    }

    public static void setRoutes(ResourceKey<Level> dim, List<SyncTeslaRoutesPacket.Entry> entries) {
        dimension = dim;
        routes = entries;
    }

    public static List<SyncTeslaRoutesPacket.Entry> routesFor(ResourceKey<Level> dim) {
        return dim.equals(dimension) ? routes : List.of();
    }

    public static List<TeslaGeometry.WaypointRef> waypointsFor(ResourceKey<Level> dim) {
        return waypointsFor(dim, null);
    }

    /** Waypoints of routes of {@code kind} only (null: every kind). */
    public static List<TeslaGeometry.WaypointRef> waypointsFor(ResourceKey<Level> dim, @Nullable RouteKind kind) {
        List<TeslaGeometry.WaypointRef> refs = new ArrayList<>();
        for (SyncTeslaRoutesPacket.Entry entry : routesFor(dim)) {
            if (kind != null && entry.kind() != kind) continue;
            List<BlockPos> points = entry.points();
            for (int i = 0; i < points.size(); i++) {
                refs.add(new TeslaGeometry.WaypointRef(entry.id(), i, points.get(i)));
            }
        }
        return refs;
    }

}
