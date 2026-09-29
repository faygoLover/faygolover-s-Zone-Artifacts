package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.tesla.TeslaEffectRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Server -> client: a Tesla just popped at {@code center}. With a {@code normal} it hit a block
 * and its bolts scatter into the half-space away from that wall; without one it popped on
 * contact with an entity and the (shorter) bolts go every which way. Bolt length follows the
 * Tesla's size, bolt count its intensity. Purely cosmetic.
 */
public class TeslaBurstPacket {

    private final Vec3 center;
    @Nullable
    private final Vec3 normal;
    private final float size;
    private final int intensity;

    public TeslaBurstPacket(Vec3 center, @Nullable Vec3 normal, float size, int intensity) {
        this.center = center;
        this.normal = normal;
        this.size = size;
        this.intensity = intensity;
    }

    public static void encode(TeslaBurstPacket packet, FriendlyByteBuf buf) {
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
    }

    public static TeslaBurstPacket decode(FriendlyByteBuf buf) {
        Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 normal = buf.readBoolean() ? new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()) : null;
        float size = buf.readFloat();
        int intensity = buf.readVarInt();
        return new TeslaBurstPacket(center, normal, size, intensity);
    }

    public static void handle(TeslaBurstPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        TeslaEffectRenderer.onBurst(packet.center, packet.normal, packet.size, packet.intensity))
        );
        ctx.get().setPacketHandled(true);
    }
}
