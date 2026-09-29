package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.entity.TeslaSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Keeps {@code ClientTeslaRouteCache} up to date: a full resync to one player on join/dimension
 * change, and a broadcast to everyone in a dimension whenever a route is created or removed (see
 * the calls from {@code TeslaRouteInteractionHandler}) — {@code AnomalySyncHandler}'s counterpart
 * for routes.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class TeslaRouteSyncHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
            sendTo(player, serverLevel);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
            sendTo(player, serverLevel);
        }
    }

    private static void sendTo(ServerPlayer player, ServerLevel level) {
        SyncTeslaRoutesPacket packet = SyncTeslaRoutesPacket.of(level.dimension(), TeslaSavedData.get(level).routes());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Call after a route is created or removed so every client watching this dimension sees it. */
    public static void broadcast(ServerLevel level) {
        SyncTeslaRoutesPacket packet = SyncTeslaRoutesPacket.of(level.dimension(), TeslaSavedData.get(level).routes());
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    private TeslaRouteSyncHandler() {
    }
}
