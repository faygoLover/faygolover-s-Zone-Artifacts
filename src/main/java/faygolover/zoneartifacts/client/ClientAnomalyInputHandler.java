package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.RemoveAnomalyPacket;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Left-click, while holding a placer item, anywhere inside an already-placed anomaly of the same
 * type removes it (like clicking a light block). This has to run client-side: a left-click at a
 * point that isn't a real block (the common case for a size-2/3 zone floating off the block grid,
 * or one that doesn't touch any solid surface at all) either never reaches the server as its own
 * event, or — for {@code LeftClickEmpty} specifically — is a client-only Forge event to begin
 * with. The actual removal still happens server-side (see {@code RemoveAnomalyPacket}), which
 * re-resolves the target itself rather than trusting this client's aim.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientAnomalyInputHandler {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        tryRemove(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        tryRemove(event.getEntity(), event);
    }

    private static void tryRemove(Player player, PlayerInteractEvent event) {
        ResourceLocation typeId = AnomalyPlacerItem.heldTypeId(player);
        if (typeId == null) return;

        Optional<SyncAnomaliesPacket.Entry> hit = AnomalyClientTargeting.pick(player, typeId);
        hit.ifPresent(entry -> {
            ModNetwork.CHANNEL.sendToServer(new RemoveAnomalyPacket(entry.typeId(), entry.pos()));
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
        });
    }
}
