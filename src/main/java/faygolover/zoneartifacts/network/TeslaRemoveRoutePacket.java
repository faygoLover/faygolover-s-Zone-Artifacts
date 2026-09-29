package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ClientTeslaCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Lightweight delta: one Tesla route was removed (deleted by a GM, or cleaned up as an orphan). */
public class TeslaRemoveRoutePacket {

    private final ResourceLocation dimension;
    private final UUID routeId;

    public TeslaRemoveRoutePacket(ResourceLocation dimension, UUID routeId) {
        this.dimension = dimension;
        this.routeId = routeId;
    }

    public TeslaRemoveRoutePacket(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        this.routeId = buf.readUUID();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeUUID(routeId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientTeslaCache.removeRoute(dimension, routeId));
        ctx.get().setPacketHandled(true);
    }
}
