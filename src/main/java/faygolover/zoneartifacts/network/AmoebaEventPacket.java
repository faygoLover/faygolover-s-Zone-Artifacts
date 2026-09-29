package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.amoeba.AmoebaClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> client: the Amoeba at {@code pos} gathers into a dome, lashes out (from {@code a} to
 *  {@code b}) or settles back into a puddle. Cosmetic; the hits are the server's. */
public class AmoebaEventPacket {

    public static final byte GATHER = 0;
    public static final byte LASH = 1;
    public static final byte SETTLE = 2;

    private final BlockPos pos;
    private final byte event;
    private final Vec3 a;
    private final Vec3 b;

    public AmoebaEventPacket(BlockPos pos, byte event, Vec3 a, Vec3 b) {
        this.pos = pos;
        this.event = event;
        this.a = a;
        this.b = b;
    }

    public static void send(ServerLevel level, BlockPos pos, byte event, Vec3 a, Vec3 b) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 96.0, level.dimension())), new AmoebaEventPacket(pos, event, a, b));
    }

    public static void encode(AmoebaEventPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeByte(packet.event);
        if (packet.event == LASH) {
            buf.writeDouble(packet.a.x);
            buf.writeDouble(packet.a.y);
            buf.writeDouble(packet.a.z);
            buf.writeDouble(packet.b.x);
            buf.writeDouble(packet.b.y);
            buf.writeDouble(packet.b.z);
        }
    }

    public static AmoebaEventPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        byte event = buf.readByte();
        Vec3 a = Vec3.ZERO;
        Vec3 b = Vec3.ZERO;
        if (event == LASH) {
            a = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            b = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
        return new AmoebaEventPacket(pos, event, a, b);
    }

    public static void handle(AmoebaEventPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AmoebaClient.onEvent(packet.pos, packet.event, packet.a, packet.b))
        );
        ctx.get().setPacketHandled(true);
    }
}
