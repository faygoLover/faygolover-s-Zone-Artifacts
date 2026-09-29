package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.network.SyncTeslaTypesPacket.Info;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Client-side copy of every loaded Tesla type's ambient-ball, bump and idle-sound config — see
 * {@code SyncTeslaTypesPacket}, sent once on login rather than per-dimension since none of this
 * varies by dimension. Everything else about a {@code TeslaType} (damage, speed, aggro radius...)
 * is server-only and never needs to leave it.
 */
public final class ClientTeslaTypeCache {

    private static Map<ResourceLocation, Info> infoByType = Map.of();

    public static void set(Map<ResourceLocation, Info> infos) {
        infoByType = infos;
    }

    @Nullable
    public static Info get(ResourceLocation typeId) {
        return infoByType.get(typeId);
    }

    private ClientTeslaTypeCache() {
    }
}
