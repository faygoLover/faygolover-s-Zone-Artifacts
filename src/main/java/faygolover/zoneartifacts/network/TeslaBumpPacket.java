package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.TeslaVisualRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client, sent the instant a Tesla bumps into solid terrain (patrolling or mid-chase):
 * a short discharge of bolts radiating outward from the impact point. Purely cosmetic — the actual
 * consequence (dropping a chase, redirecting) is already decided server-side in {@code
 * TeslaEntity#onBump} regardless of whether this ever renders.
 */
public class TeslaBumpPacket {

    private final Vec3 pos;
    private final int color;
    private final int boltCount;
    private final double reach;
    private final int durationTicks;

    public TeslaBumpPacket(Vec3 pos, int color, int boltCount, double reach, int durationTicks) {
        this.pos = pos;
        this.color = color;
        this.boltCount = boltCount;
        this.reach = reach;
        this.durationTicks = durationTicks;
    }

    public static void encode(TeslaBumpPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.pos.x);
        buf.writeDouble(packet.pos.y);
        buf.writeDouble(packet.pos.z);
        buf.writeInt(packet.color);
        buf.writeVarInt(packet.boltCount);
        buf.writeDouble(packet.reach);
        buf.writeVarInt(packet.durationTicks);
    }

    public static TeslaBumpPacket decode(FriendlyByteBuf buf) {
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int color = buf.readInt();
        int boltCount = buf.readVarInt();
        double reach = buf.readDouble();
        int durationTicks = buf.readVarInt();
        return new TeslaBumpPacket(pos, color, boltCount, reach, durationTicks);
    }

    public static void handle(TeslaBumpPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        TeslaVisualRenderer.onBump(packet.pos, packet.color, packet.boltCount, packet.reach, packet.durationTicks))
        );
        ctx.get().setPacketHandled(true);
    }
}
