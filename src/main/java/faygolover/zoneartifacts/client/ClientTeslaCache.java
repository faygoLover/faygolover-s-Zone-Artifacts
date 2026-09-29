package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.network.TeslaSyncRoutesPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side mirror of Tesla route state: every finalized route in the current dimension
 * (rendered cyan, visible to everyone) plus this client's own in-progress build, if any
 * (rendered yellow, visible only to the building player — the server only ever sends it to them).
 */
public final class ClientTeslaCache {

    private static ResourceLocation currentDimension = null;
    private static final Map<UUID, List<BlockPos>> ROUTES = new HashMap<>();
    private static List<BlockPos> ownBuildSession = List.of();

    private ClientTeslaCache() {
    }

    public static void replaceRoutes(ResourceLocation dimension, List<TeslaSyncRoutesPacket.Entry> entries) {
        currentDimension = dimension;
        ROUTES.clear();
        for (TeslaSyncRoutesPacket.Entry entry : entries) {
            ROUTES.put(entry.routeId(), entry.points());
        }
    }

    public static void removeRoute(ResourceLocation dimension, UUID routeId) {
        if (!dimension.equals(currentDimension)) return;
        ROUTES.remove(routeId);
    }

    public static void setOwnBuildSession(List<BlockPos> points) {
        ownBuildSession = points;
    }

    public static Map<UUID, List<BlockPos>> routes() {
        return ROUTES;
    }

    public static List<BlockPos> ownBuildSession() {
        return ownBuildSession;
    }
}
