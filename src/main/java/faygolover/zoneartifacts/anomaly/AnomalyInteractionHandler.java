package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Right-click with a placer while aiming into an existing anomaly's zone does nothing: anomalies
 * are placed only on blocks, never into or onto another anomaly. (Right-click used to cycle the
 * size; that's the size tuner's job since 0.1.3.0.) Without this, the click would go through to the
 * block behind the zone and place a second anomaly right next to the first.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class AnomalyInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (aimsAtZone(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean aimsAtZone(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        ResourceLocation typeId = AnomalyPlacerItem.heldTypeId(player);
        if (typeId == null) return false;
        return AnomalyTargeting.pick(serverLevel, player, null).isPresent();
    }
}
