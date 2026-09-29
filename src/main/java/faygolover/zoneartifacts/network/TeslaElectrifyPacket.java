package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.TeslaVisualRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -&gt; client, sent the instant a Tesla lands a hit: "wrap entity {@code targetEntityId} in
 * arcs across its own surface for {@code durationTicks}." This is the "electrify the target like
 * in a cartoon" visual — separate from the ambient ball {@code TeslaVisualRenderer} already draws
 * around Tesla herself, and outliving her: she's discarded the same tick this is sent, but the
 * effect plays out on the target regardless of whether she's still around to see it.
 */
public class TeslaElectrifyPacket {

    private final int targetEntityId;
    private final int durationTicks;
    private final int color;

    public TeslaElectrifyPacket(int targetEntityId, int durationTicks, int color) {
        this.targetEntityId = targetEntityId;
        this.durationTicks = durationTicks;
        this.color = color;
    }

    public static void encode(TeslaElectrifyPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.targetEntityId);
        buf.writeVarInt(packet.durationTicks);
        buf.writeInt(packet.color);
    }

    public static TeslaElectrifyPacket decode(FriendlyByteBuf buf) {
        int targetEntityId = buf.readVarInt();
        int durationTicks = buf.readVarInt();
        int color = buf.readInt();
        return new TeslaElectrifyPacket(targetEntityId, durationTicks, color);
    }

    public static void handle(TeslaElectrifyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        TeslaVisualRenderer.onElectrify(packet.targetEntityId, packet.durationTicks, packet.color))
        );
        ctx.get().setPacketHandled(true);
    }
}
