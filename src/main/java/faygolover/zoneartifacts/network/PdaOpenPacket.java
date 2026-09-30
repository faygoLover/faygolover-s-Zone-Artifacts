package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.pda.PdaService;
import faygolover.zoneartifacts.pda.PdaTarget;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the KPK was used on {@code target}; the server answers with a {@link PdaDataPacket}. */
public record PdaOpenPacket(PdaTarget target) {

    public static void encode(PdaOpenPacket packet, FriendlyByteBuf buf) {
        packet.target.encode(buf);
    }

    public static PdaOpenPacket decode(FriendlyByteBuf buf) {
        return new PdaOpenPacket(PdaTarget.decode(buf));
    }

    public static void handle(PdaOpenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) PdaService.open(player, packet.target);
        });
        ctx.get().setPacketHandled(true);
    }
}
