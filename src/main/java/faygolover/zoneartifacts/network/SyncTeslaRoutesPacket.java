package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.tesla.TeslaClientCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: every Tesla route in a dimension — completed ones and drafts still being
 * built — so a GM holding the placer can see and click them. A rendering/click aid only; every
 * action is re-validated on the server.
 */
public class SyncTeslaRoutesPacket {

    /** {@code id > 0}: completed route; {@code id < 0}: draft. */
    public record Entry(int id, boolean complete, List<BlockPos> points) {
    }

    private final ResourceKey<Level> dimension;
    private final List<Entry> entries;

    public SyncTeslaRoutesPacket(ResourceKey<Level> dimension, List<Entry> entries) {
        this.dimension = dimension;
        this.entries = entries;
    }

    public static void encode(SyncTeslaRoutesPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.dimension.location());
        buf.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buf.writeInt(entry.id());
            buf.writeBoolean(entry.complete());
            buf.writeVarInt(entry.points().size());
            for (BlockPos pos : entry.points()) {
                buf.writeBlockPos(pos);
            }
        }
    }

    public static SyncTeslaRoutesPacket decode(FriendlyByteBuf buf) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buf.readInt();
            boolean complete = buf.readBoolean();
            int pointCount = buf.readVarInt();
            List<BlockPos> points = new ArrayList<>(pointCount);
            for (int j = 0; j < pointCount; j++) {
                points.add(buf.readBlockPos());
            }
            entries.add(new Entry(id, complete, List.copyOf(points)));
        }
        return new SyncTeslaRoutesPacket(dimension, entries);
    }

    public static void handle(SyncTeslaRoutesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TeslaClientCache.setRoutes(packet.dimension, packet.entries))
        );
        ctx.get().setPacketHandled(true);
    }
}
