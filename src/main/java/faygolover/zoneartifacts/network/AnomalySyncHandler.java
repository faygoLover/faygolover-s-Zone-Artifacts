package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyInstance;
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
 * dimension's anomalies change structurally (see the call from AnomalyPlacerItem /
 * AnomalyInteractionHandler / RemoveAnomalyPacket). A plain cooldown flip is far more frequent
 * than any of those and doesn't need the whole list resent — see {@link #broadcastCooldown}.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class AnomalySyncHandler {

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
        SyncAnomaliesPacket packet = SyncAnomaliesPacket.of(level.dimension(), AnomalySavedData.get(level).instances());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Call after any add/remove/tuning change so every client watching this dimension sees it. */
    public static void broadcast(ServerLevel level) {
        SyncAnomaliesPacket packet = SyncAnomaliesPacket.of(level.dimension(), AnomalySavedData.get(level).instances());
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    /**
     * Cheap alternative to {@link #broadcast} for the one thing that flips far more often than
     * anything structural: a burst anomaly's own cooldown flag starting or ending. Patches just
     * that one instance client-side instead of re-sending every anomaly in the dimension to every
     * player in it — with N anomalies each firing every few seconds, that's the difference between
     * an O(1) packet and an O(N) one on every single trigger.
     */
    public static void broadcastCooldown(ServerLevel level, AnomalyInstance instance, boolean onCooldown) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),
                new SyncAnomalyCooldownPacket(instance.typeId(), instance.pos(), onCooldown));
    }
}
