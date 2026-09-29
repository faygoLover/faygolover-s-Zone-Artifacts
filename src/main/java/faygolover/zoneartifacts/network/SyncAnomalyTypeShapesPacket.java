package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.anomaly.AnomalyType;
import faygolover.zoneartifacts.anomaly.AnomalyTypeManager;
import faygolover.zoneartifacts.client.ClientAnomalyTypeCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server -> client, sent once on login: every loaded anomaly type's per-level sizes. This is the
 * piece that lets the client size a preview/highlight box at all — datapack content
 * ({@code AnomalyTypeManager}) only ever loads server-side, so on a real dedicated server the
 * client would otherwise have no idea how big "level 2" is.
 */
public class SyncAnomalyTypeShapesPacket {

    private final Map<ResourceLocation, List<Integer>> sizesByLevelByType;

    public SyncAnomalyTypeShapesPacket(Map<ResourceLocation, List<Integer>> sizesByLevelByType) {
        this.sizesByLevelByType = sizesByLevelByType;
    }

    public static SyncAnomalyTypeShapesPacket ofAllLoadedTypes() {
        Map<ResourceLocation, List<Integer>> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, AnomalyType> entry : AnomalyTypeManager.all().entrySet()) {
            map.put(entry.getKey(), entry.getValue().shape().sizesByLevel());
        }
        return new SyncAnomalyTypeShapesPacket(map);
    }

    public static void encode(SyncAnomalyTypeShapesPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.sizesByLevelByType.size());
        for (Map.Entry<ResourceLocation, List<Integer>> entry : packet.sizesByLevelByType.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (int size : entry.getValue()) {
                buf.writeVarInt(size);
            }
        }
    }

    public static SyncAnomalyTypeShapesPacket decode(FriendlyByteBuf buf) {
        int typeCount = buf.readVarInt();
        Map<ResourceLocation, List<Integer>> map = new HashMap<>();
        for (int i = 0; i < typeCount; i++) {
            ResourceLocation id = buf.readResourceLocation();
            int levelCount = buf.readVarInt();
            List<Integer> sizes = new ArrayList<>(levelCount);
            for (int j = 0; j < levelCount; j++) {
                sizes.add(buf.readVarInt());
            }
            map.put(id, sizes);
        }
        return new SyncAnomalyTypeShapesPacket(map);
    }

    public static void handle(SyncAnomalyTypeShapesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAnomalyTypeCache.set(packet.sizesByLevelByType))
        );
        ctx.get().setPacketHandled(true);
    }
}
