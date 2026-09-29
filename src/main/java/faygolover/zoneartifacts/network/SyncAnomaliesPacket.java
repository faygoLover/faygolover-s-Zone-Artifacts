package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Full resync of every placed anomaly in one dimension. Sent when a player (re)joins that
 * dimension, and whenever an anomaly is placed or has its level changed — the only two changes
 * that add or alter data the client doesn't already have. A plain removal uses the far cheaper
 * {@link RemoveAnomalyPacket} instead of resending the whole list.
 */
public class SyncAnomaliesPacket {

    private final ResourceLocation dimension;
    private final List<Entry> entries;

    public SyncAnomaliesPacket(ResourceLocation dimension, List<Entry> entries) {
        this.dimension = dimension;
        this.entries = entries;
    }

    public SyncAnomaliesPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        int count = buf.readVarInt();
        List<Entry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation typeId = buf.readResourceLocation();
            BlockPos pos = buf.readBlockPos();
            int level = buf.readVarInt();
            list.add(new Entry(typeId, pos, level));
        }
        this.entries = list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeResourceLocation(entry.typeId());
            buf.writeBlockPos(entry.pos());
            buf.writeVarInt(entry.level());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientAnomalyCache.replaceAll(dimension, entries));
        ctx.get().setPacketHandled(true);
    }

    public static Entry entryOf(AnomalyInstance instance) {
        return new Entry(instance.typeId(), instance.pos(), instance.level());
    }

    public record Entry(ResourceLocation typeId, BlockPos pos, int level) {
    }
}
