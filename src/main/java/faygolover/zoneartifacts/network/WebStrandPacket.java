package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.web.WebClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> clients: a thread of a Web snapped (at {@code at}) or grew back. */
public class WebStrandPacket {

    private final int webId;
    private final int index;
    private final boolean broken;
    private final Vec3 at;

    public WebStrandPacket(int webId, int index, boolean broken, Vec3 at) {
        this.webId = webId;
        this.index = index;
        this.broken = broken;
        this.at = at;
    }

    public static void send(ServerLevel level, int webId, int index, boolean broken, Vec3 at) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), new WebStrandPacket(webId, index, broken, at));
    }

    public static void encode(WebStrandPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.webId);
        buf.writeVarInt(packet.index);
        buf.writeBoolean(packet.broken);
        buf.writeDouble(packet.at.x);
        buf.writeDouble(packet.at.y);
        buf.writeDouble(packet.at.z);
    }

    public static WebStrandPacket decode(FriendlyByteBuf buf) {
        return new WebStrandPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public static void handle(WebStrandPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WebClient.onStrand(packet.webId, packet.index, packet.broken, packet.at))
        );
        ctx.get().setPacketHandled(true);
    }
}
