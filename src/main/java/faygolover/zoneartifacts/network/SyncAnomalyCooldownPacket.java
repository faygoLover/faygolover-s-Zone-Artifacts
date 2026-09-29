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
 * no reason — this just patches that one entry client-side ({@link ClientAnomalyCache#updateState})
 * instead. The full packet is still used for actually structural changes (placement, removal,
 * level change, join, dimension change), where the whole list genuinely needs resending.
 * <p>
 * Since 0.1.5.0 it also carries the thermal anomalies' {@code active} flag (someone inside).
 */
public class SyncAnomalyCooldownPacket {

    private final ResourceLocation typeId;
    private final BlockPos pos;
    private final boolean onCooldown;
    private final boolean active;

    public SyncAnomalyCooldownPacket(ResourceLocation typeId, BlockPos pos, boolean onCooldown, boolean active) {
        this.typeId = typeId;
        this.pos = pos;
        this.onCooldown = onCooldown;
        this.active = active;
    }

    public static void encode(SyncAnomalyCooldownPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.typeId);
        buf.writeBlockPos(packet.pos);
        buf.writeBoolean(packet.onCooldown);
        buf.writeBoolean(packet.active);
    }

    public static SyncAnomalyCooldownPacket decode(FriendlyByteBuf buf) {
        ResourceLocation typeId = buf.readResourceLocation();
        BlockPos pos = buf.readBlockPos();
        boolean onCooldown = buf.readBoolean();
        boolean active = buf.readBoolean();
        return new SyncAnomalyCooldownPacket(typeId, pos, onCooldown, active);
    }

    public static void handle(SyncAnomalyCooldownPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientAnomalyCache.updateState(packet.typeId, packet.pos, packet.onCooldown, packet.active))
        );
        ctx.get().setPacketHandled(true);
    }
}
