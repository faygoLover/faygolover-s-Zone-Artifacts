package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.AnomalyArcRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client, sent once per struck target the instant a zone anomaly fires: draw bolts
 * converging from the zone's surface points onto {@code targetEntityId} (see
 * {@link AnomalyArcRenderer#onStrike}). Carries the zone's size and intensity so the client needs
 * no other lookup. Purely cosmetic and fire-and-forget.
 */
public class AnomalyStrikePacket {

    private final ResourceLocation typeId;
    private final BlockPos pos;
    private final float size;
    private final int intensity;
    private final int targetEntityId;

    public AnomalyStrikePacket(ResourceLocation typeId, BlockPos pos, float size, int intensity, int targetEntityId) {
        this.typeId = typeId;
        this.pos = pos;
        this.size = size;
        this.intensity = intensity;
        this.targetEntityId = targetEntityId;
    }

    public static void encode(AnomalyStrikePacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.typeId);
        buf.writeBlockPos(packet.pos);
        buf.writeFloat(packet.size);
        buf.writeVarInt(packet.intensity);
        buf.writeVarInt(packet.targetEntityId);
    }

    public static AnomalyStrikePacket decode(FriendlyByteBuf buf) {
        ResourceLocation typeId = buf.readResourceLocation();
        BlockPos pos = buf.readBlockPos();
        float size = buf.readFloat();
        int intensity = buf.readVarInt();
        int targetEntityId = buf.readVarInt();
        return new AnomalyStrikePacket(typeId, pos, size, intensity, targetEntityId);
    }

    public static void handle(AnomalyStrikePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        AnomalyArcRenderer.onStrike(packet.typeId, packet.pos, packet.size, packet.intensity, packet.targetEntityId))
        );
        ctx.get().setPacketHandled(true);
    }
}
