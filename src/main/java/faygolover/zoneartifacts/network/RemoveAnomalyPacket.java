package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ClientAnomalyCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Lightweight delta: one anomaly was removed. Cheaper than a full {@link SyncAnomaliesPacket}
 * resync since nothing about any other anomaly's data changed.
 */
public class RemoveAnomalyPacket {

    private final ResourceLocation dimension;
    private final ResourceLocation typeId;
    private final BlockPos pos;

    public RemoveAnomalyPacket(ResourceLocation dimension, ResourceLocation typeId, BlockPos pos) {
        this.dimension = dimension;
        this.typeId = typeId;
        this.pos = pos;
    }

    public RemoveAnomalyPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        this.typeId = buf.readResourceLocation();
        this.pos = buf.readBlockPos();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeResourceLocation(typeId);
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientAnomalyCache.remove(dimension, typeId, pos));
        ctx.get().setPacketHandled(true);
    }
}
