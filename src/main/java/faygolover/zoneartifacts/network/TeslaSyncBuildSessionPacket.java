package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ClientTeslaCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sent only to the player currently building a Tesla route: their own in-progress points (yellow
 * while building). An empty list means "you have no build in progress" — used to clear the
 * client-side preview after a finalize, cancel, or session-loss discard.
 */
public class TeslaSyncBuildSessionPacket {

    private final List<BlockPos> points;

    public TeslaSyncBuildSessionPacket(List<BlockPos> points) {
        this.points = points;
    }

    public TeslaSyncBuildSessionPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BlockPos> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(buf.readBlockPos());
        }
        this.points = list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(points.size());
        for (BlockPos p : points) {
            buf.writeBlockPos(p);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientTeslaCache.setOwnBuildSession(points));
        ctx.get().setPacketHandled(true);
    }
}
