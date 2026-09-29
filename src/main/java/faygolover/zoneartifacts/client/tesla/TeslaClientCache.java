package faygolover.zoneartifacts.client.tesla;

import faygolover.zoneartifacts.network.SyncTeslaRoutesPacket;
import faygolover.zoneartifacts.tesla.TeslaGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

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
        List<TeslaGeometry.WaypointRef> refs = new ArrayList<>();
        for (SyncTeslaRoutesPacket.Entry entry : routesFor(dim)) {
            List<BlockPos> points = entry.points();
            for (int i = 0; i < points.size(); i++) {
                refs.add(new TeslaGeometry.WaypointRef(entry.id(), i, points.get(i)));
            }
        }
        return refs;
    }

}
