package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import faygolover.zoneartifacts.tesla.TeslaRouteSavedData;
import faygolover.zoneartifacts.tesla.TeslaType;
import faygolover.zoneartifacts.tesla.TeslaTypeIds;
import faygolover.zoneartifacts.tesla.TeslaTypeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.stream.Collectors;

/** The only place server code builds and sends Tesla-related packets. */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class TeslaSyncHandler {

    private TeslaSyncHandler() {
    }

    public static void broadcastRoutesFullResync(ServerLevel level) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), buildRoutesPacket(level));
    }

    public static void broadcastRouteRemoved(ServerLevel level, java.util.UUID routeId) {
        TeslaRemoveRoutePacket packet = new TeslaRemoveRoutePacket(level.dimension().location(), routeId);
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    public static void sendBuildSession(ServerPlayer player, List<net.minecraft.core.BlockPos> points) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TeslaSyncBuildSessionPacket(points));
    }

    public static void sendType(ServerPlayer player) {
        TeslaType type = TeslaTypeManager.get(TeslaTypeIds.TESLA);
        if (type == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TeslaSyncTypePacket(type.color(), type.idleSound(), type.idleVolume(), type.idlePitch()));
    }

    public static void broadcastElectrify(ServerLevel level, TeslaEntity tesla, int targetEntityId, int durationTicks) {
        TeslaElectrifyPacket packet = new TeslaElectrifyPacket(tesla.getId(), targetEntityId, durationTicks);
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    public static void broadcastBlockBurst(ServerLevel level, Vec3 point, int rayCount, double distance, int color) {
        TeslaBlockBurstPacket packet = new TeslaBlockBurstPacket(point, rayCount, distance, color);
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    private static TeslaSyncRoutesPacket buildRoutesPacket(ServerLevel level) {
        List<TeslaSyncRoutesPacket.Entry> entries = TeslaRouteSavedData.get(level).routes().values().stream()
                .map(TeslaSyncRoutesPacket::entryOf)
                .collect(Collectors.toList());
        return new TeslaSyncRoutesPacket(level.dimension().location(), entries);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendType(player);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildRoutesPacket(player.serverLevel()));
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildRoutesPacket(player.serverLevel()));
            // A dimension change always discards any in-progress build (regardless of exactly
            // when TeslaBuildManager's own listener runs relative to this one) — tell the client
            // to clear its (now stale) yellow preview unconditionally.
            sendBuildSession(player, List.of());
        }
    }
}
