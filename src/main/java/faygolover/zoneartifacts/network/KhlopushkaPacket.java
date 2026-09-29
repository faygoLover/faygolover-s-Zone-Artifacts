package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.khlopushka.KhlopushkaClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server -> clients: a Khlopushka clot appeared ({@code ticks} to its blast), blasted
 *  ({@code value} = radius), or — to one player — its flash blinded them ({@code value} = strength). */
public class KhlopushkaPacket {

    public static final int SPAWN = 0;
    public static final int BLAST = 1;
    public static final int FLASH = 2;

    private final int type;
    private final Vec3 at;
    private final float value;
    private final int ticks;

    public KhlopushkaPacket(int type, Vec3 at, float value, int ticks) {
        this.type = type;
        this.at = at;
        this.value = value;
        this.ticks = ticks;
    }

    public static void spawn(ServerLevel level, Vec3 at, int ticks) {
        near(level, at, new KhlopushkaPacket(SPAWN, at, 0.0f, ticks));
    }

    public static void blast(ServerLevel level, Vec3 at, float radius) {
        near(level, at, new KhlopushkaPacket(BLAST, at, radius, 0));
    }

    public static void flash(ServerPlayer player, float strength, int ticks) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new KhlopushkaPacket(FLASH, player.position(), strength, ticks));
    }

    private static void near(ServerLevel level, Vec3 at, KhlopushkaPacket packet) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 96.0, level.dimension())), packet);
    }

    public static void encode(KhlopushkaPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.type);
        buf.writeDouble(packet.at.x);
        buf.writeDouble(packet.at.y);
        buf.writeDouble(packet.at.z);
        buf.writeFloat(packet.value);
        buf.writeVarInt(packet.ticks);
    }

    public static KhlopushkaPacket decode(FriendlyByteBuf buf) {
        return new KhlopushkaPacket(buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(KhlopushkaPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KhlopushkaClient.onPacket(packet.type, packet.at, packet.value, packet.ticks))
        );
        ctx.get().setPacketHandled(true);
    }
}
