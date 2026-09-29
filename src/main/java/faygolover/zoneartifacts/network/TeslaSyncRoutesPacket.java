package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ClientTeslaCache;
import faygolover.zoneartifacts.tesla.TeslaRouteSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Full resync of every finalized Tesla route in one dimension — sent on join/dimension-change and
 * whenever a route is finalized. Mirrors {@code SyncAnomaliesPacket}'s role for Electra; route
 * removal uses the cheaper {@link TeslaRemoveRoutePacket} instead of resending everything.
 */
public class TeslaSyncRoutesPacket {

    private final ResourceLocation dimension;
    private final List<Entry> routes;

    public TeslaSyncRoutesPacket(ResourceLocation dimension, List<Entry> routes) {
        this.dimension = dimension;
        this.routes = routes;
    }

    public TeslaSyncRoutesPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        int routeCount = buf.readVarInt();
        List<Entry> list = new ArrayList<>(routeCount);
        for (int i = 0; i < routeCount; i++) {
            UUID routeId = buf.readUUID();
            int pointCount = buf.readVarInt();
            List<BlockPos> points = new ArrayList<>(pointCount);
            for (int p = 0; p < pointCount; p++) {
                points.add(buf.readBlockPos());
            }
            list.add(new Entry(routeId, points));
        }
        this.routes = list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeVarInt(routes.size());
        for (Entry entry : routes) {
            buf.writeUUID(entry.routeId());
            buf.writeVarInt(entry.points().size());
            for (BlockPos p : entry.points()) {
                buf.writeBlockPos(p);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientTeslaCache.replaceRoutes(dimension, routes));
        ctx.get().setPacketHandled(true);
    }

    public static Entry entryOf(TeslaRouteSavedData.TeslaRoute route) {
        return new Entry(route.routeId(), route.points());
    }

    public record Entry(UUID routeId, List<BlockPos> points) {
    }
}
