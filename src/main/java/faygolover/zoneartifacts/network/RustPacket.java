package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.rust.RustClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> clients: Rust's red-hot patch — heating up ({@link #SPOT}, {@code age} ticks old), gone
 *  cold ({@link #CLEAR}), gone off ({@link #BLAST}) or cooled by a snowball ({@link #DISCHARGE}). */
public class RustPacket {

    public static final int SPOT = 0;
    public static final int CLEAR = 1;
    public static final int BLAST = 2;
    public static final int DISCHARGE = 3;

    private final BlockPos zone;
    private final int type;
    private final Vec3 at;
    private final int age;

    public RustPacket(BlockPos zone, int type, Vec3 at, int age) {
        this.zone = zone;
        this.type = type;
        this.at = at;
        this.age = age;
    }

    public static void send(ServerLevel level, BlockPos zone, int type, Vec3 at, int age) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 96.0, level.dimension())),
                new RustPacket(zone, type, at, age));
    }

    public static void encode(RustPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.zone);
        buf.writeByte(packet.type);
        buf.writeDouble(packet.at.x);
        buf.writeDouble(packet.at.y);
        buf.writeDouble(packet.at.z);
        buf.writeVarInt(packet.age);
    }

    public static RustPacket decode(FriendlyByteBuf buf) {
        return new RustPacket(buf.readBlockPos(), buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt());
    }

    public static void handle(RustPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RustClient.onPacket(packet.zone, packet.type, packet.at, packet.age))
        );
        ctx.get().setPacketHandled(true);
    }
}
