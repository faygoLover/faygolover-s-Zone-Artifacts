package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.client.ClientTeslaRouteCache;
import faygolover.zoneartifacts.entity.TeslaRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -&gt; client: every Tesla route's waypoints in this dimension — purely so {@code
 * TeslaRouteHighlightRenderer} can draw a completed loop while a GM holds the route tool, exactly
 * like {@code SyncAnomaliesPacket} does for Electra's placer. Never authoritative: a click always
 * re-resolves against the real, server-only {@code TeslaSavedData} (see {@code
 * TeslaRouteInteractionHandler}). Sent on join/dimension change and whenever a route is created or
 * removed (see {@code TeslaRouteSyncHandler}).
 */
public class SyncTeslaRoutesPacket {

    private final ResourceKey<Level> dimension;
    private final List<Entry> entries;

    public SyncTeslaRoutesPacket(ResourceKey<Level> dimension, List<Entry> entries) {
        this.dimension = dimension;
        this.entries = entries;
    }

    public static SyncTeslaRoutesPacket of(ResourceKey<Level> dimension, List<TeslaRoute> routes) {
        List<Entry> entries = new ArrayList<>(routes.size());
        for (TeslaRoute route : routes) {
            entries.add(new Entry(route.id(), route.typeId(), List.copyOf(route.waypoints())));
        }
        return new SyncTeslaRoutesPacket(dimension, entries);
    }

    public static void encode(SyncTeslaRoutesPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.dimension.location());
        buf.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buf.writeVarInt(entry.id());
            buf.writeResourceLocation(entry.typeId());
            buf.writeVarInt(entry.waypoints().size());
            for (BlockPos pos : entry.waypoints()) {
                buf.writeBlockPos(pos);
            }
        }
    }

    public static SyncTeslaRoutesPacket decode(FriendlyByteBuf buf) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buf.readVarInt();
            ResourceLocation typeId = buf.readResourceLocation();
            int waypointCount = buf.readVarInt();
            List<BlockPos> waypoints = new ArrayList<>(waypointCount);
            for (int j = 0; j < waypointCount; j++) {
                waypoints.add(buf.readBlockPos());
            }
            entries.add(new Entry(id, typeId, waypoints));
        }
        return new SyncTeslaRoutesPacket(dimension, entries);
    }

    public static void handle(SyncTeslaRoutesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTeslaRouteCache.set(packet.dimension, packet.entries))
        );
        ctx.get().setPacketHandled(true);
    }

    public record Entry(int id, ResourceLocation typeId, List<BlockPos> waypoints) {
    }
}
