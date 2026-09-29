package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.gravi.GraviClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Server -> client: a Gravi pop starts sucking in at {@code pos} (on a surface facing {@code normal}
 *  made of block state {@code stateId}, or in the air). It bursts {@code Gravi.WINDUP_TICKS} later. */
public class GraviPopPacket {

    private final Vec3 pos;
    @Nullable
    private final Vec3 normal;
    private final int stateId;

    public GraviPopPacket(Vec3 pos, @Nullable Vec3 normal, int stateId) {
        this.pos = pos;
        this.normal = normal;
        this.stateId = stateId;
    }

    public static void encode(GraviPopPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.pos.x);
        buf.writeDouble(packet.pos.y);
        buf.writeDouble(packet.pos.z);
        buf.writeBoolean(packet.normal != null);
        if (packet.normal != null) {
            buf.writeByte((int) Math.round(packet.normal.x));
            buf.writeByte((int) Math.round(packet.normal.y));
            buf.writeByte((int) Math.round(packet.normal.z));
        }
        buf.writeVarInt(packet.stateId);
    }

    public static GraviPopPacket decode(FriendlyByteBuf buf) {
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 normal = buf.readBoolean() ? new Vec3(buf.readByte(), buf.readByte(), buf.readByte()) : null;
        return new GraviPopPacket(pos, normal, buf.readVarInt());
    }

    public static void handle(GraviPopPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GraviClient.onPop(packet.pos, packet.normal, packet.stateId))
        );
        ctx.get().setPacketHandled(true);
    }
}
