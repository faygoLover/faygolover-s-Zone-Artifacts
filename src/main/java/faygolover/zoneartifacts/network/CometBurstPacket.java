package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.tesla.CometEffectRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Server -> client: a Comet (or, {@code cold}, a Cold Comet) just exploded at {@code center}. With a {@code normal} it flew into a
 * block and the flames burst away from that wall; without one it exploded on an entity and they
 * go every which way. Size scales the blast, intensity the amount of fire. Purely cosmetic.
 */
public class CometBurstPacket {

    private final Vec3 center;
    @Nullable
    private final Vec3 normal;
    private final float size;
    private final int intensity;
    private final boolean cold;

    public CometBurstPacket(Vec3 center, @Nullable Vec3 normal, float size, int intensity, boolean cold) {
        this.center = center;
        this.normal = normal;
        this.size = size;
        this.intensity = intensity;
        this.cold = cold;
    }

    public static void encode(CometBurstPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.center.x);
        buf.writeDouble(packet.center.y);
        buf.writeDouble(packet.center.z);
        buf.writeBoolean(packet.normal != null);
        if (packet.normal != null) {
            buf.writeFloat((float) packet.normal.x);
            buf.writeFloat((float) packet.normal.y);
            buf.writeFloat((float) packet.normal.z);
        }
        buf.writeFloat(packet.size);
        buf.writeVarInt(packet.intensity);
        buf.writeBoolean(packet.cold);
    }

    public static CometBurstPacket decode(FriendlyByteBuf buf) {
        Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 normal = buf.readBoolean() ? new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()) : null;
        float size = buf.readFloat();
        int intensity = buf.readVarInt();
        boolean cold = buf.readBoolean();
        return new CometBurstPacket(center, normal, size, intensity, cold);
    }

    public static void handle(CometBurstPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        CometEffectRenderer.onBurst(packet.center, packet.normal, packet.size, packet.intensity, packet.cold))
        );
        ctx.get().setPacketHandled(true);
    }
}
