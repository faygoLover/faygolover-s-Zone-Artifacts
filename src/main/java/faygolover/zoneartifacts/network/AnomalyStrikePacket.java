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
 * Server -> client, sent once per struck target the instant a burst anomaly fires: tells the
 * client to draw lightning bolts converging from the zone's own (surface) anchor points onto
 * {@code targetEntityId} — or, when nothing living was hit and only a thrown projectile tripped
 * the field, onto the zone's own center instead ({@code targetEntityId == -1}, see {@link
 * AnomalyArcRenderer#onStrike}). This is what replaced the old particle burst: instead of a
 * generic mid-air puff, the always-on ambient arcs (also {@code AnomalyArcRenderer}) get one
 * dramatic moment where they actually reach out and hit something.
 * <p>
 * Purely cosmetic and fire-and-forget — same spirit as the rest of the visual layer here, the
 * server doesn't need the client to acknowledge or agree on anything about this packet.
 */
public class AnomalyStrikePacket {

    private final ResourceLocation typeId;
    private final BlockPos pos;
    private final int level;
    private final int targetEntityId;

    public AnomalyStrikePacket(ResourceLocation typeId, BlockPos pos, int level, int targetEntityId) {
        this.typeId = typeId;
        this.pos = pos;
        this.level = level;
        this.targetEntityId = targetEntityId;
    }

    public static void encode(AnomalyStrikePacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.typeId);
        buf.writeBlockPos(packet.pos);
        buf.writeVarInt(packet.level);
        // -1 means "no living target, strike the zone's own center" — writeVarInt/readVarInt
        // round-trip negative values fine (just at zig-zag-free full width), same trick already
        // used elsewhere in this mod's networking for "no value" sentinels.
        buf.writeVarInt(packet.targetEntityId);
    }

    public static AnomalyStrikePacket decode(FriendlyByteBuf buf) {
        ResourceLocation typeId = buf.readResourceLocation();
        BlockPos pos = buf.readBlockPos();
        int level = buf.readVarInt();
        int targetEntityId = buf.readVarInt();
        return new AnomalyStrikePacket(typeId, pos, level, targetEntityId);
    }

    public static void handle(AnomalyStrikePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        AnomalyArcRenderer.onStrike(packet.typeId, packet.pos, packet.level, packet.targetEntityId))
        );
        ctx.get().setPacketHandled(true);
    }
}
