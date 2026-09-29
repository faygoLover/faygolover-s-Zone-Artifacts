package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaRoutePlacerItem;
import faygolover.zoneartifacts.network.TeslaSync;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side glue for Tesla routes: resetting unfinished routes when their builder leaves
 * (logout, dimension change, server stop), keeping clients' route lists in sync,
 * and stopping the placer from opening doors/chests when a GM clicks one to put a waypoint next to it.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class TeslaServerEvents {

    private TeslaServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            TeslaSync.sendTo(player, level);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeslaDrafts.Draft draft = TeslaDrafts.remove(player.getUUID());
            if (draft != null) {
                ServerLevel level = player.server.getLevel(draft.dimension());
                if (level != null) TeslaSync.broadcast(level);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        TeslaDrafts.Draft draft = TeslaDrafts.remove(player.getUUID());
        if (draft != null) {
            ServerLevel oldLevel = player.server.getLevel(draft.dimension());
            if (oldLevel != null) TeslaSync.broadcast(oldLevel);
            player.sendSystemMessage(Component.translatable("message.fl_zone_arts.tesla.draft_reset_dimension",
                    TeslaRouteService.formatPos(draft.start())));
        }
        if (player.level() instanceof ServerLevel newLevel) {
            TeslaSync.sendTo(player, newLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TeslaDrafts.clear();
    }

    /** With the placer in hand, a right-click on a door, chest or lever places a waypoint next to
     *  it instead of using the block. Runs on both sides so client and server agree. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof TeslaRoutePlacerItem) {
            event.setUseBlock(Event.Result.DENY);
        }
    }
}
