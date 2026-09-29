package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ClientAnomalyCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client, sent instead of a full {@link SyncAnomaliesPacket} whenever a single placed
 * anomaly's cooldown flag flips (a burst firing, or its cooldown running out). A burst-type
 * anomaly flips this every time it's tripped, and re-serializing/re-sending every anomaly in the
 * whole dimension to every player in it on each single flip doesn't scale with anomaly count for
 * no reason — this just patches that one entry client-side ({@link ClientAnomalyCache#updateCooldown})
 * instead. The full packet is still used for actually structural changes (placement, removal,
 * level change, join, dimension change), where the whole list genuinely needs resending.
 */
public class SyncAnomalyCooldownPacket {

    private final ResourceLocation typeId;
    private final BlockPos pos;
    private final boolean onCooldown;

    public SyncAnomalyCooldownPacket(ResourceLocation typeId, BlockPos pos, boolean onCooldown) {
        this.typeId = typeId;
        this.pos = pos;
        this.onCooldown = onCooldown;
    }

    public static void encode(SyncAnomalyCooldownPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.typeId);
        buf.writeBlockPos(packet.pos);
        buf.writeBoolean(packet.onCooldown);
    }

    public static SyncAnomalyCooldownPacket decode(FriendlyByteBuf buf) {
        ResourceLocation typeId = buf.readResourceLocation();
        BlockPos pos = buf.readBlockPos();
        boolean onCooldown = buf.readBoolean();
        return new SyncAnomalyCooldownPacket(typeId, pos, onCooldown);
    }

    public static void handle(SyncAnomalyCooldownPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientAnomalyCache.updateCooldown(packet.typeId, packet.pos, packet.onCooldown))
        );
        ctx.get().setPacketHandled(true);
    }
}
