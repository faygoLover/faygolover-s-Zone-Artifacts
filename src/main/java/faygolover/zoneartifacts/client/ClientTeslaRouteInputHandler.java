package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaRouteToolItem;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.TeslaRouteClickPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Left-click fallback for {@link TeslaRouteToolItem}, for the one case direct block-click events
 * can't cover: a waypoint whose marker block has already been broken, now floating in open air.
 * {@code LeftClickEmpty} is a client-only Forge event fired whenever a left-click's own raytrace
 * finds no block at all, so that's when {@code TeslaRouteClientTargeting}'s own (non-authoritative)
 * check runs and, on a hit, sends {@link TeslaRouteClickPacket} for the server to act on for real.
 * Mirrors {@code ClientAnomalyInputHandler} exactly. Right-click needs no such fallback -
 * {@code RightClickItem} reaches the server directly, so it's handled fully server-side (see
 * {@code TeslaRouteInteractionHandler#onRightClickItem} / {@code TeslaRouteTargeting}).
 * <p>
 * While the waypoint is still a real block, {@code LeftClickBlock} fires instead and reaches
 * {@code TeslaRouteInteractionHandler} directly - this class never runs for that case.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientTeslaRouteInputHandler {

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        ItemStack stack = TeslaRouteToolItem.heldStack(player);
        if (stack == null) return;

        TeslaRouteClientTargeting.pick(player).ifPresentOrElse(pos -> {
            ModNetwork.CHANNEL.sendToServer(new TeslaRouteClickPacket(pos));
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
        }, () -> {
            // Nothing within reach even with the generous fallback box - purely local feedback (no
            // server round trip needed to say "there was nothing to hit").
            List<BlockPos> chain = TeslaRouteToolItem.getChain(stack);
            if (!chain.isEmpty()) {
                MutableComponent message = Component.literal("fl_zone_arts: Нет цели в досягаемости. Начало: ")
                        .append(Component.literal(chain.get(0).toShortString()).withStyle(ChatFormatting.AQUA));
                player.displayClientMessage(message, true);
            }
        });
    }
}
