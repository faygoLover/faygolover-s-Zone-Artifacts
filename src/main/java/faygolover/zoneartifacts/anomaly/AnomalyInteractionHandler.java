package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Left-click-while-holding-a-placer interaction with an already-placed anomaly: cycles its level,
 * the same way a vanilla light block cycles its light level on repeated clicks. Right-click
 * (deletion, and placing a brand new anomaly) lives directly in {@link AnomalyPlacerItem} instead,
 * since vanilla calls an item's own {@code useOn}/{@code use} methods for that — but left-click has
 * no equivalent per-item hook, so it has to be caught here as a generic interaction event.
 * <p>
 * Targeting goes through {@link AnomalyTargeting}, not the vanilla block hit result, so this
 * fires correctly anywhere inside an anomaly's zone (including levels whose zone extends past the
 * single anchor block that was originally clicked to place it), not only when the exact anchor
 * block happens to be under the crosshair.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class AnomalyInteractionHandler {

    private AnomalyInteractionHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;

        Player player = event.getEntity();
        ResourceLocation typeId = AnomalyPlacerItem.typeIdOf(player.getMainHandItem());
        if (typeId == null) return;

        AnomalyTargeting.pick(serverLevel, player, typeId).ifPresent(instance -> {
            event.setCanceled(true);
            cycleLevel(serverLevel, instance);
        });
    }

    private static void cycleLevel(ServerLevel level, AnomalyInstance instance) {
        AnomalyType type = AnomalyTypeManager.get(instance.typeId());
        if (type == null) return;

        int nextLevel = instance.level() % type.maxLevel() + 1;
        instance.setLevel(nextLevel);
        AnomalySavedData.get(level).setDirty();
        AnomalySyncHandler.broadcastFullResync(level);
    }
}
