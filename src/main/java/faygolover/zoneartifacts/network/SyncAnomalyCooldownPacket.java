package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ClientAnomalyCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Lightweight delta for the one thing that flips constantly: an anomaly going on/off cooldown.
 * Drives both the ambient arc visuals and the looping idle sound on the client. Sent instead of a
 * full resync since nothing about the anomaly's data (type/position/level) changed — this is the
 * packet that made the old "resend everything on every cooldown flip" approach unnecessary.
 */
public class SyncAnomalyCooldownPacket {

    private final ResourceLocation dimension;
    private final ResourceLocation typeId;
    private final BlockPos pos;
    private final boolean onCooldown;

    public SyncAnomalyCooldownPacket(ResourceLocation dimension, ResourceLocation typeId, BlockPos pos, boolean onCooldown) {
        this.dimension = dimension;
        this.typeId = typeId;
        this.pos = pos;
        this.onCooldown = onCooldown;
    }

    public SyncAnomalyCooldownPacket(FriendlyByteBuf buf) {
        this.dimension = buf.readResourceLocation();
        this.typeId = buf.readResourceLocation();
        this.pos = buf.readBlockPos();
        this.onCooldown = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeResourceLocation(typeId);
        buf.writeBlockPos(pos);
        buf.writeBoolean(onCooldown);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientAnomalyCache.setCooldown(dimension, typeId, pos, onCooldown));
        ctx.get().setPacketHandled(true);
    }
}
