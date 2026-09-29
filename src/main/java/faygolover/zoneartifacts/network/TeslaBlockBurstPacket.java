package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.TeslaEffectRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sent when Tesla dies against a solid block: lightning rays radiate outward from the impact point. */
public class TeslaBlockBurstPacket {

    private final Vec3 point;
    private final int rayCount;
    private final double distance;
    private final int color;

    public TeslaBlockBurstPacket(Vec3 point, int rayCount, double distance, int color) {
        this.point = point;
        this.rayCount = rayCount;
        this.distance = distance;
        this.color = color;
    }

    public TeslaBlockBurstPacket(FriendlyByteBuf buf) {
        this.point = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.rayCount = buf.readVarInt();
        this.distance = buf.readDouble();
        this.color = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
        buf.writeVarInt(rayCount);
        buf.writeDouble(distance);
        buf.writeVarInt(color);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> TeslaEffectRenderer.onBlockBurst(point, rayCount, distance, color));
        ctx.get().setPacketHandled(true);
    }
}
