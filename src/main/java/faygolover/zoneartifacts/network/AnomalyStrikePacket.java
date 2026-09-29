package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.AnomalyArcRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent once per hit target when an anomaly triggers (one packet per living entity, one per
 * projectile — never collapsed to a single zone-center sentinel). Tells the client to strike real
 * lightning bolts from the zone's surface anchor points onto that specific target, after a short
 * windup, instead of the old center-of-zone particle burst.
 */
public class AnomalyStrikePacket {

    private final ResourceLocation dimension;
    private final ResourceLocation typeId;
    private final BlockPos pos;
    private final int targetEntityId;

    public AnomalyStrikePacket(ResourceLocation dimension, ResourceLocation typeId, BlockPos pos, int targetEntityId) {
        this.dimension = dimension;
        this.typeId = typeId;
        this.pos = pos;
        this.targetEntityId = targetEntityId;
    }

    public AnomalyStrikePacket(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        this.typeId = buf.readResourceLocation();
        this.pos = buf.readBlockPos();
        this.targetEntityId = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeResourceLocation(typeId);
        buf.writeBlockPos(pos);
        buf.writeVarInt(targetEntityId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> AnomalyArcRenderer.onStrike(dimension, typeId, pos, targetEntityId));
        ctx.get().setPacketHandled(true);
    }
}
