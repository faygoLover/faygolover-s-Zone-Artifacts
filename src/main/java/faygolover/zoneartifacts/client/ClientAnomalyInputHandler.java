package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side half of {@code AnomalyInteractionHandler}: suppresses the block-breaking
 * animation/hit-particles a left-click would otherwise start playing locally — client-side input
 * prediction fires {@code PlayerInteractEvent.LeftClickBlock} too, separately from the server's
 * own copy of the event — when the crosshair is really aiming at an anomaly's zone rather than
 * the block underneath it. The real level-cycle action is still decided authoritatively on the
 * server (see {@code AnomalyInteractionHandler}), which re-resolves the same target independently;
 * this class only ever suppresses a visual glitch, it never changes real state itself.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT)
public final class ClientAnomalyInputHandler {

    private ClientAnomalyInputHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) return;

        ResourceLocation typeId = AnomalyPlacerItem.typeIdOf(player.getMainHandItem());
        if (typeId == null) return;

        AnomalyClientTargeting.pick(typeId).ifPresent(entry -> event.setCanceled(true));
    }
}
