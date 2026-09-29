package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.razlom.RazlomClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Server -> client, every tick while a Razlom's jet burns: where the jet's end is aimed right now
 * ({@code aim}), at whom ({@code targetId}, -1 = nobody, it's coasting) and for how many more ticks.
 * {@code ticks = 0}: the jet is over. Only sent to players near the Razlom.
 */
public class RazlomJetPacket {

    private static final double SEND_RADIUS = 96.0;

    private final BlockPos pos;
    private final int targetId;
    private final int ticks;
    private final Vec3 aim;

    public RazlomJetPacket(BlockPos pos, int targetId, int ticks, Vec3 aim) {
        this.pos = pos;
        this.targetId = targetId;
        this.ticks = ticks;
        this.aim = aim;
    }

    public static void send(ServerLevel level, BlockPos pos, int targetId, int ticks, Vec3 aim) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SEND_RADIUS, level.dimension())),
                new RazlomJetPacket(pos, targetId, ticks, aim));
    }

    public static void encode(RazlomJetPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeInt(packet.targetId);
        buf.writeVarInt(packet.ticks);
        buf.writeFloat((float) packet.aim.x);
        buf.writeFloat((float) packet.aim.y);
        buf.writeFloat((float) packet.aim.z);
    }

    public static RazlomJetPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int targetId = buf.readInt();
        int ticks = buf.readVarInt();
        Vec3 aim = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new RazlomJetPacket(pos, targetId, ticks, aim);
    }

    public static void handle(RazlomJetPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        RazlomClientHandler.onJet(packet.pos, packet.targetId, packet.ticks, packet.aim))
        );
        ctx.get().setPacketHandled(true);
    }
}
