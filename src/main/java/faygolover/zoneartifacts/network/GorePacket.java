package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.gravity.GoreClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Server -> client: entity {@code entityId} was just torn apart by a Voronka or a Karusel — its
 * body is hidden and bursts into blood: droplets flying off and splashes on the blocks around.
 */
public class GorePacket {

    private final int entityId;
    private final double x;
    private final double y;
    private final double z;
    private final float width;
    private final float height;

    public GorePacket(int entityId, double x, double y, double z, float width, float height) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.width = width;
        this.height = height;
    }

    public static void send(Entity entity) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new GorePacket(entity.getId(), entity.getX(), entity.getY(), entity.getZ(), entity.getBbWidth(), entity.getBbHeight()));
    }

    public static void encode(GorePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.width);
        buf.writeFloat(packet.height);
    }

    public static GorePacket decode(FriendlyByteBuf buf) {
        return new GorePacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(GorePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        GoreClient.onGore(packet.entityId, packet.x, packet.y, packet.z, packet.width, packet.height))
        );
        ctx.get().setPacketHandled(true);
    }
}
