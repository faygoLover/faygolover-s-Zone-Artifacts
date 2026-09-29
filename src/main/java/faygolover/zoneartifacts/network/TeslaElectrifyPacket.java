package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.TeslaEffectRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent when Tesla makes contact with a living entity: tells the client to render arcs crawling
 * across that entity's own surface for the given duration ("электрофицируя её как в мультике").
 */
public class TeslaElectrifyPacket {

    private final int teslaEntityId;
    private final int targetEntityId;
    private final int durationTicks;

    public TeslaElectrifyPacket(int teslaEntityId, int targetEntityId, int durationTicks) {
        this.teslaEntityId = teslaEntityId;
        this.targetEntityId = targetEntityId;
        this.durationTicks = durationTicks;
    }

    public TeslaElectrifyPacket(FriendlyByteBuf buf) {
        this.teslaEntityId = buf.readVarInt();
        this.targetEntityId = buf.readVarInt();
        this.durationTicks = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(teslaEntityId);
        buf.writeVarInt(targetEntityId);
        buf.writeVarInt(durationTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> TeslaEffectRenderer.onElectrify(teslaEntityId, targetEntityId, durationTicks));
        ctx.get().setPacketHandled(true);
    }
}
