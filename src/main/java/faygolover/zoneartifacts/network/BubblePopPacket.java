package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.bubbles.BubbleClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> clients: a soap bubble burst at {@code at} with blast {@code radius}. */
public class BubblePopPacket {

    private final Vec3 at;
    private final float radius;

    public BubblePopPacket(Vec3 at, float radius) {
        this.at = at;
        this.radius = radius;
    }

    public static void send(ServerLevel level, Vec3 at, float radius) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 64.0, level.dimension())),
                new BubblePopPacket(at, radius));
    }

    public static void encode(BubblePopPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.at.x);
        buf.writeDouble(packet.at.y);
        buf.writeDouble(packet.at.z);
        buf.writeFloat(packet.radius);
    }

    public static BubblePopPacket decode(FriendlyByteBuf buf) {
        return new BubblePopPacket(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat());
    }

    public static void handle(BubblePopPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BubbleClient.onPop(packet.at, packet.radius))
        );
        ctx.get().setPacketHandled(true);
    }
}
