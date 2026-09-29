package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Right-click, while holding a placer item, anywhere inside an already-placed anomaly of the same
 * type cycles its level (like clicking a light block) — aiming doesn't need to land on the anchor
 * block exactly. This runs fully server-side: {@code RightClickBlock} and {@code RightClickItem}
 * both reach the server reliably regardless of what the client's own block raytrace found, so
 * unlike the left-click/remove case (see {@code ClientAnomalyInputHandler}) no client packet is
 * needed here — {@link AnomalyTargeting} re-derives the target straight from the real, server-side
 * {@link AnomalySavedData}.
 * <p>
 * If the raytrace finds nothing, the event is left alone and falls through to normal vanilla
 * handling — {@link AnomalyPlacerItem#useOn} places a new anomaly on right-click as before.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class AnomalyInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        tryCycle(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        tryCycle(event.getEntity(), event);
    }

    private static void tryCycle(Player player, PlayerInteractEvent event) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        ResourceLocation typeId = AnomalyPlacerItem.heldTypeId(player);
        if (typeId == null) return;

        AnomalyType type = AnomalyTypeManager.get(typeId);
        if (type == null) return;

        Optional<AnomalyInstance> hit = AnomalyTargeting.pick(serverLevel, player, typeId);
        hit.ifPresent(instance -> {
            int nextLevel = instance.level() % type.maxLevel() + 1;
            instance.setLevel(nextLevel);
            AnomalySavedData.get(serverLevel).setDirty();
            notify(player, typeId + " at " + instance.pos().toShortString() + " -> level " + nextLevel);
            AnomalySyncHandler.broadcast(serverLevel);
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
        });
    }

    private static void notify(Player player, String message) {
        player.displayClientMessage(Component.literal("fl_zone_arts: " + message), true);
    }
}
