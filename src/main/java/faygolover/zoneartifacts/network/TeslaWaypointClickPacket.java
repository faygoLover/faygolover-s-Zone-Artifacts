package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.tesla.TeslaRouteService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: "I left-clicked waypoint {@code index} of route {@code routeId}, which sits at
 * {@code pos}". Left-clicks on points floating in the air never reach the server on their own
 * ({@code LeftClickEmpty} is client-only), hence the packet — same reason as Electra's
 * {@link RemoveAnomalyPacket}. The server trusts none of it blindly: it re-checks the held item,
 * the distance, and that {@code pos} really is that waypoint right now (so a duplicate or stale
 * packet can never remove a different point that shifted into the same index).
 */
public class TeslaWaypointClickPacket {

    private final int routeId;
    private final int index;
    private final BlockPos pos;

    public TeslaWaypointClickPacket(int routeId, int index, BlockPos pos) {
        this.routeId = routeId;
        this.index = index;
        this.pos = pos;
    }

    public static void encode(TeslaWaypointClickPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.routeId);
        buf.writeVarInt(packet.index);
        buf.writeBlockPos(packet.pos);
    }

    public static TeslaWaypointClickPacket decode(FriendlyByteBuf buf) {
        return new TeslaWaypointClickPacket(buf.readInt(), buf.readVarInt(), buf.readBlockPos());
    }

    public static void handle(TeslaWaypointClickPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                TeslaRouteService.onLeftClickWaypoint(player, packet.routeId, packet.index, packet.pos);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
