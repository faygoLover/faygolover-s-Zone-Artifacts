package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Keeps {@code ClientAnomalyCache} (via SyncAnomaliesPacket) up to date: a full resync to one
 * player on join/dimension change, and a broadcast to everyone in a dimension whenever that
 * dimension's anomalies change (see the call from AnomalyPlacerItem / AnomalyInteractionHandler).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class AnomalySyncHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
            // Type shapes only need sending once per session (they don't change per-dimension);
            // instances are sent both here and on every dimension change.
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), SyncAnomalyTypeShapesPacket.ofAllLoadedTypes());
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
        SyncAnomaliesPacket packet = SyncAnomaliesPacket.of(level.dimension(), AnomalySavedData.get(level).instances());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Call after any add/remove/level change so every client watching this dimension sees it. */
    public static void broadcast(ServerLevel level) {
        SyncAnomaliesPacket packet = SyncAnomaliesPacket.of(level.dimension(), AnomalySavedData.get(level).instances());
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }
}
