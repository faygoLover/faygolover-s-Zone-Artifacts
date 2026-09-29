package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.tesla.TeslaEffectRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: a Tesla shocked entity {@code entityId}; draw arcs crawling over its surface
 * for {@code durationTicks}. Sent to everyone tracking the entity, and to the entity itself when
 * it's a player, so the victim sees their own electrification too.
 */
public class TeslaElectrifyPacket {

    private final int entityId;
    private final int durationTicks;

    public TeslaElectrifyPacket(int entityId, int durationTicks) {
        this.entityId = entityId;
        this.durationTicks = durationTicks;
    }

    public static void encode(TeslaElectrifyPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeVarInt(packet.durationTicks);
    }

    public static TeslaElectrifyPacket decode(FriendlyByteBuf buf) {
        return new TeslaElectrifyPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(TeslaElectrifyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        TeslaEffectRenderer.onElectrify(packet.entityId, packet.durationTicks))
        );
        ctx.get().setPacketHandled(true);
    }
}
