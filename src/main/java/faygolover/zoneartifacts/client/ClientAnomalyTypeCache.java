package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Client-side mirror of the handful of per-type fields synced via {@link SyncAnomalyTypeShapesPacket}. */
public final class ClientAnomalyTypeCache {

    private static Map<ResourceLocation, SyncAnomalyTypeShapesPacket.TypeShape> TYPES = Map.of();

    private ClientAnomalyTypeCache() {
    }

    public static void replaceAll(Map<ResourceLocation, SyncAnomalyTypeShapesPacket.TypeShape> types) {
        TYPES = Map.copyOf(types);
    }

    public static SyncAnomalyTypeShapesPacket.TypeShape get(ResourceLocation typeId) {
        return TYPES.get(typeId);
    }

    public static int sizeForLevel(SyncAnomalyTypeShapesPacket.TypeShape shape, int level) {
        java.util.List<Integer> sizes = shape.sizesByLevel();
        int index = Math.max(1, Math.min(level, sizes.size())) - 1;
        return sizes.get(index);
    }
}
