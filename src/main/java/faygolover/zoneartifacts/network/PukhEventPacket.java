package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.pukh.PukhClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> client, Burning Fluff: a spore puff shot from {@code a} at {@code b} ({@code range}
 *  blocks), or its strands crumbling away ({@code pos}, {@code facing}, {@code range} = length). */
public class PukhEventPacket {

    public static final byte PUFF = 0;
    public static final byte CRUMBLE = 1;

    private final byte event;
    private final Vec3 a;
    private final Vec3 b;
    private final double range;
    private final BlockPos pos;
    private final Direction facing;

    private PukhEventPacket(byte event, Vec3 a, Vec3 b, double range, BlockPos pos, Direction facing) {
        this.event = event;
        this.a = a;
        this.b = b;
        this.range = range;
        this.pos = pos;
        this.facing = facing;
    }

    public static void puff(ServerLevel level, Vec3 from, Vec3 at, double range) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(from.x, from.y, from.z, 64.0, level.dimension())),
                new PukhEventPacket(PUFF, from, at, range, BlockPos.containing(from), Direction.DOWN));
    }

    public static void crumble(ServerLevel level, BlockPos pos, Direction facing, double length) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 64.0, level.dimension())),
                new PukhEventPacket(CRUMBLE, Vec3.ZERO, Vec3.ZERO, length, pos, facing));
    }

    public static void encode(PukhEventPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.event);
        if (packet.event == PUFF) {
            buf.writeDouble(packet.a.x);
            buf.writeDouble(packet.a.y);
            buf.writeDouble(packet.a.z);
            buf.writeDouble(packet.b.x);
            buf.writeDouble(packet.b.y);
            buf.writeDouble(packet.b.z);
        } else {
            buf.writeBlockPos(packet.pos);
            buf.writeEnum(packet.facing);
        }
        buf.writeDouble(packet.range);
    }

    public static PukhEventPacket decode(FriendlyByteBuf buf) {
        byte event = buf.readByte();
        if (event == PUFF) {
            Vec3 a = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 b = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            return new PukhEventPacket(PUFF, a, b, buf.readDouble(), BlockPos.ZERO, Direction.DOWN);
        }
        BlockPos pos = buf.readBlockPos();
        Direction facing = buf.readEnum(Direction.class);
        return new PukhEventPacket(event, Vec3.ZERO, Vec3.ZERO, buf.readDouble(), pos, facing);
    }

    public static void handle(PukhEventPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if (packet.event == PUFF) PukhClient.onPuff(packet.a, packet.b, packet.range);
                    else PukhClient.onCrumble(packet.pos, packet.facing, packet.range);
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
