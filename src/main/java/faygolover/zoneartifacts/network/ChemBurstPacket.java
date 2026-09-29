package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.chem.ChemClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: a Chemical Comet burst at {@code center}; its cloud settles on {@code groundY},
 *  spreads to {@code radius} and lingers {@code seconds}. Purely cosmetic (the harm is server-side). */
public class ChemBurstPacket {

    private final Vec3 center;
    private final double groundY;
    private final float radius;
    private final float seconds;
    private final int intensity;

    public ChemBurstPacket(Vec3 center, double groundY, float radius, float seconds, int intensity) {
        this.center = center;
        this.groundY = groundY;
        this.radius = radius;
        this.seconds = seconds;
        this.intensity = intensity;
    }

    public static void encode(ChemBurstPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.center.x);
        buf.writeDouble(packet.center.y);
        buf.writeDouble(packet.center.z);
        buf.writeDouble(packet.groundY);
        buf.writeFloat(packet.radius);
        buf.writeFloat(packet.seconds);
        buf.writeVarInt(packet.intensity);
    }

    public static ChemBurstPacket decode(FriendlyByteBuf buf) {
        return new ChemBurstPacket(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(ChemBurstPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ChemClient.onBurst(packet.center, packet.groundY, packet.radius, packet.seconds, packet.intensity))
        );
        ctx.get().setPacketHandled(true);
    }
}
