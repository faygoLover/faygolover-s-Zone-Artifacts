package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.tesla.TeslaDrafts;
import faygolover.zoneartifacts.tesla.TeslaRoute;
import faygolover.zoneartifacts.tesla.TeslaRouteSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Builds and sends {@link SyncTeslaRoutesPacket}s. Routes change rarely (only by GM clicks and
 *  commands), so the whole dimension's list is simply resent on every change. */
public final class TeslaSync {

    private TeslaSync() {
    }

    public static SyncTeslaRoutesPacket snapshot(ServerLevel level) {
        List<SyncTeslaRoutesPacket.Entry> entries = new ArrayList<>();
        for (TeslaRoute route : TeslaRouteSavedData.get(level).routes()) {
            entries.add(new SyncTeslaRoutesPacket.Entry(route.id(), route.kind(), true, route.waypoints()));
        }
        for (TeslaDrafts.Draft draft : TeslaDrafts.inDimension(level.dimension())) {
            entries.add(new SyncTeslaRoutesPacket.Entry(draft.id(), draft.kind(), false, List.copyOf(draft.points())));
        }
        return new SyncTeslaRoutesPacket(level.dimension(), entries);
    }

    public static void broadcast(ServerLevel level) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), snapshot(level));
    }

    public static void sendTo(ServerPlayer player, ServerLevel level) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot(level));
    }
}
