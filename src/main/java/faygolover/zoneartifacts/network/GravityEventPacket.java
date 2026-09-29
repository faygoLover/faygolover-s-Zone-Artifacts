package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.gravity.GravityClientHandler;
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
 * Server -> client: something happened to the gravitational anomaly at {@code pos}:
 * {@link #START} (a pull/spin phase began, with the phase's random {@code seed} — orbits are
 * derived from it), {@link #RELEASE} (Plesh throw / Voronka tear / Karusel hit) or {@link #BOUNCE}
 * (something bounced off a Podushka at {@code at}). Drives the visuals and, for {@code START} /
 * {@code RELEASE}, when the client pulls its own player.
 */
public class GravityEventPacket {

    public static final byte START = 0;
    public static final byte RELEASE = 1;
    public static final byte BOUNCE = 2;

    private static final double SEND_RADIUS = 96.0;

    private final BlockPos pos;
    private final byte event;
    private final int seed;
    private final Vec3 at;

    public GravityEventPacket(BlockPos pos, byte event, int seed, Vec3 at) {
        this.pos = pos;
        this.event = event;
        this.seed = seed;
        this.at = at;
    }

    public static void send(ServerLevel level, BlockPos pos, byte event, int seed, Vec3 at) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SEND_RADIUS, level.dimension())),
                new GravityEventPacket(pos, event, seed, at));
    }

    public static void encode(GravityEventPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeByte(packet.event);
        buf.writeInt(packet.seed);
        buf.writeFloat((float) packet.at.x);
        buf.writeFloat((float) packet.at.y);
        buf.writeFloat((float) packet.at.z);
    }

    public static GravityEventPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        byte event = buf.readByte();
        int seed = buf.readInt();
        Vec3 at = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new GravityEventPacket(pos, event, seed, at);
    }

    public static void handle(GravityEventPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        GravityClientHandler.onEvent(packet.pos, packet.event, packet.seed, packet.at))
        );
        ctx.get().setPacketHandled(true);
    }
}
