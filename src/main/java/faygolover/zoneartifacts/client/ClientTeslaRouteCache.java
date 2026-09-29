package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.network.SyncTeslaRoutesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Client-side snapshot of "what Tesla routes exist and where their waypoints are", refreshed
 * wholesale whenever a {@link SyncTeslaRoutesPacket} arrives — {@code ClientAnomalyCache}'s
 * counterpart. Purely a rendering aid for {@code TeslaRouteHighlightRenderer}; never authoritative.
 */
public final class ClientTeslaRouteCache {

    private static ResourceKey<Level> dimension = null;
    private static List<SyncTeslaRoutesPacket.Entry> entries = List.of();

    public static void set(ResourceKey<Level> dim, List<SyncTeslaRoutesPacket.Entry> newEntries) {
        dimension = dim;
        entries = newEntries;
    }

    public static List<SyncTeslaRoutesPacket.Entry> entriesFor(ResourceKey<Level> dim) {
        return dim.equals(dimension) ? entries : List.of();
    }

    private ClientTeslaRouteCache() {
    }
}
