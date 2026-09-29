package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.fog.AcidFogClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> client: a jet of acid vapour bursts up out of an Acid Fog at {@code at} (ground level). */
public class FogJetPacket {

    private final Vec3 at;
    private final int intensity;

    public FogJetPacket(Vec3 at, int intensity) {
        this.at = at;
        this.intensity = intensity;
    }

    public static void send(ServerLevel level, Vec3 at, int intensity) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 80.0, level.dimension())),
                new FogJetPacket(at, intensity));
    }

    public static void encode(FogJetPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.at.x);
        buf.writeDouble(packet.at.y);
        buf.writeDouble(packet.at.z);
        buf.writeVarInt(packet.intensity);
    }

    public static FogJetPacket decode(FriendlyByteBuf buf) {
        return new FogJetPacket(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt());
    }

    public static void handle(FogJetPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AcidFogClient.onJet(packet.at, packet.intensity))
        );
        ctx.get().setPacketHandled(true);
    }
}
