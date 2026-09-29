package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import faygolover.zoneartifacts.anomaly.AnomalyType;
import faygolover.zoneartifacts.anomaly.AnomalyTypeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The only place server code builds and sends {@link ModNetwork} packets — the tick engine, the
 * placer item and the interaction handler all call one of the named methods here instead of
 * constructing packets themselves, so every call site agrees on exactly what triggers a full
 * resync versus a lightweight delta.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class AnomalySyncHandler {

    private AnomalySyncHandler() {
    }

    /** New anomaly placed, or an existing one's level changed — the client needs the new data. */
    public static void broadcastFullResync(ServerLevel level) {
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), buildResyncPacket(level));
    }

    /** Anomaly removed — nothing about any other instance's data changed, so skip the full resync. */
    public static void broadcastRemoval(ServerLevel level, AnomalyInstance instance) {
        RemoveAnomalyPacket packet = new RemoveAnomalyPacket(
                level.dimension().location(), instance.typeId(), instance.pos());
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    /** The frequent one: an anomaly flipped on/off cooldown. */
    public static void broadcastCooldown(ServerLevel level, AnomalyInstance instance, boolean onCooldown) {
        SyncAnomalyCooldownPacket packet = new SyncAnomalyCooldownPacket(
                level.dimension().location(), instance.typeId(), instance.pos(), onCooldown);
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    /** One real lightning bolt, targeted at one specific hit entity. */
    public static void broadcastStrike(ServerLevel level, AnomalyInstance instance, int targetEntityId) {
        AnomalyStrikePacket packet = new AnomalyStrikePacket(
                level.dimension().location(), instance.typeId(), instance.pos(), targetEntityId);
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    public static void sendTypeShapes(ServerPlayer player) {
        Map<ResourceLocation, SyncAnomalyTypeShapesPacket.TypeShape> shapes = new HashMap<>();
        for (Map.Entry<ResourceLocation, AnomalyType> entry : AnomalyTypeManager.all().entrySet()) {
            shapes.put(entry.getKey(), toTypeShape(entry.getValue()));
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncAnomalyTypeShapesPacket(shapes));
    }

    public static void sendFullResync(ServerPlayer player, ServerLevel level) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildResyncPacket(level));
    }

    private static SyncAnomalyTypeShapesPacket.TypeShape toTypeShape(AnomalyType type) {
        SyncAnomalyTypeShapesPacket.ArcShape arcShape = type.arc() == null ? null
                : new SyncAnomalyTypeShapesPacket.ArcShape(
                        type.arc().bundleCount(), type.arc().pointsPerBundle(),
                        type.arc().minLifetimeTicks(), type.arc().maxLifetimeTicks(), type.arc().color());

        ResourceLocation ambientSound = type.ambient() == null ? null : type.ambient().sound();
        float ambientVolume = type.ambient() == null ? 1.0f : type.ambient().soundVolume();
        float ambientPitch = type.ambient() == null ? 1.0f : type.ambient().soundPitch();

        return new SyncAnomalyTypeShapesPacket.TypeShape(type.shape().sizesByLevel(), arcShape,
                ambientSound, ambientVolume, ambientPitch);
    }

    private static SyncAnomaliesPacket buildResyncPacket(ServerLevel level) {
        List<SyncAnomaliesPacket.Entry> entries = AnomalySavedData.get(level).instances().stream()
                .map(SyncAnomaliesPacket::entryOf)
                .collect(Collectors.toList());
        return new SyncAnomaliesPacket(level.dimension().location(), entries);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTypeShapes(player);
            sendFullResync(player, player.serverLevel());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendFullResync(player, player.serverLevel());
        }
    }
}
