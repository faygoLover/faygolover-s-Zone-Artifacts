package faygolover.zoneartifacts.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;

import java.util.List;

/**
 * Client-side snapshot of "what anomalies exist and where", refreshed wholesale whenever a
 * {@link SyncAnomaliesPacket} arrives (join, dimension change, or any placement/removal/level
 * change). Purely a rendering aid for {@code AnomalyHighlightRenderer} — never authoritative,
 * the server always re-checks for real before acting on a click (see AnomalyTargeting).
 */
public final class ClientAnomalyCache {

    private static ResourceKey<Level> dimension = null;
    private static List<SyncAnomaliesPacket.Entry> entries = List.of();

    public static void set(ResourceKey<Level> dim, List<SyncAnomaliesPacket.Entry> newEntries) {
        dimension = dim;
        entries = newEntries;
    }

    public static List<SyncAnomaliesPacket.Entry> entriesFor(ResourceKey<Level> dim) {
        return dim.equals(dimension) ? entries : List.of();
    }

    private ClientAnomalyCache() {
    }
}
