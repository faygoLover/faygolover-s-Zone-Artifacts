package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.pda.PdaView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> client: what the KPK window shows (opens it, or refreshes the open one). */
public record PdaDataPacket(PdaView view) {

    public static void send(ServerPlayer player, PdaView view) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PdaDataPacket(view));
    }

    public static void encode(PdaDataPacket packet, FriendlyByteBuf buf) {
        packet.view.encode(buf);
    }

    public static PdaDataPacket decode(FriendlyByteBuf buf) {
        return new PdaDataPacket(PdaView.decode(buf));
    }

    public static void handle(PdaDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> faygolover.zoneartifacts.client.pda.PdaScreen.show(packet.view)));
        ctx.get().setPacketHandled(true);
    }
}
