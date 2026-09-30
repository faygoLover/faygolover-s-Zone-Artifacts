package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.pda.PdaService;
import faygolover.zoneartifacts.pda.PdaTarget;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client -> server: the KPK window's values for {@code target} (by setting id), its switches (-1:
 * none), a move by one block along each axis given (-1, 0, 1), or {@code reset} to the standards; or {@code delete}:
 * remove the anomaly altogether.
 * The server clamps everything itself ({@link PdaService#apply}).
 */
public record PdaApplyPacket(PdaTarget target, Map<String, Double> values, int switches, int dx, int dy, int dz, boolean reset,
                             boolean delete) {

    public static void encode(PdaApplyPacket packet, FriendlyByteBuf buf) {
        packet.target.encode(buf);
        buf.writeVarInt(packet.values.size());
        packet.values.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeDouble(v);
        });
        buf.writeInt(packet.switches);
        buf.writeByte(packet.dx);
        buf.writeByte(packet.dy);
        buf.writeByte(packet.dz);
        buf.writeBoolean(packet.reset);
        buf.writeBoolean(packet.delete);
    }

    public static PdaApplyPacket decode(FriendlyByteBuf buf) {
        PdaTarget target = PdaTarget.decode(buf);
        int n = Math.min(buf.readVarInt(), 32);
        Map<String, Double> values = new HashMap<>();
        for (int i = 0; i < n; i++) values.put(buf.readUtf(64), buf.readDouble());
        return new PdaApplyPacket(target, values, buf.readInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean(),
                buf.readBoolean());
    }

    public static void handle(PdaApplyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && packet.delete) {
                PdaService.delete(player, packet.target);
            } else if (player != null) {
                PdaService.apply(player, packet.target, packet.values, packet.switches, packet.dx, packet.dy, packet.dz, packet.reset);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
