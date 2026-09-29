package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.amoeba.AmoebaClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> client: the Amoeba at {@code pos} starts gathering itself up, or bursts. Cosmetic
 *  (its cloud comes with a {@link ChemBurstPacket}). */
public class AmoebaEventPacket {

    public static final byte GATHER = 0;
    public static final byte POP = 1;

    private final BlockPos pos;
    private final byte event;

    public AmoebaEventPacket(BlockPos pos, byte event) {
        this.pos = pos;
        this.event = event;
    }

    public static void send(ServerLevel level, BlockPos pos, byte event) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 96.0, level.dimension())), new AmoebaEventPacket(pos, event));
    }

    public static void encode(AmoebaEventPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeByte(packet.event);
    }

    public static AmoebaEventPacket decode(FriendlyByteBuf buf) {
        return new AmoebaEventPacket(buf.readBlockPos(), buf.readByte());
    }

    public static void handle(AmoebaEventPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AmoebaClient.onEvent(packet.pos, packet.event))
        );
        ctx.get().setPacketHandled(true);
    }
}
