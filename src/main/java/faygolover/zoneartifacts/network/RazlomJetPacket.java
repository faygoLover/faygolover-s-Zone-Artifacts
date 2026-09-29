package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.razlom.RazlomClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Server -> client: the Razlom at {@code pos} now aims its fire jet at entity {@code targetId}
 * for {@code ticks} more ticks ({@code targetId = -1}: the jet is over). Sent when a jet starts,
 * switches target and ends. Drives the jet visuals and its sound.
 */
public class RazlomJetPacket {

    private final BlockPos pos;
    private final int targetId;
    private final int ticks;

    public RazlomJetPacket(BlockPos pos, int targetId, int ticks) {
        this.pos = pos;
        this.targetId = targetId;
        this.ticks = ticks;
    }

    public static void send(ServerLevel level, BlockPos pos, int targetId, int ticks) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), new RazlomJetPacket(pos, targetId, ticks));
    }

    public static void encode(RazlomJetPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeInt(packet.targetId);
        buf.writeVarInt(packet.ticks);
    }

    public static RazlomJetPacket decode(FriendlyByteBuf buf) {
        return new RazlomJetPacket(buf.readBlockPos(), buf.readInt(), buf.readVarInt());
    }

    public static void handle(RazlomJetPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        RazlomClientHandler.onJet(packet.pos, packet.targetId, packet.ticks))
        );
        ctx.get().setPacketHandled(true);
    }
}
