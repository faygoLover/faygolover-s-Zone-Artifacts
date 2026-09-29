package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaRoutePlacerItem;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.SyncTeslaConfigPacket;
import faygolover.zoneartifacts.network.TeslaSync;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side glue for Tesla routes: resetting unfinished routes when their builder leaves
 * (logout, dimension change, server stop), keeping clients' route lists and Tesla config in sync,
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

    /** Fires on login (one player) and after every /reload (all players). */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncTeslaConfigPacket packet = SyncTeslaConfigPacket.of(TeslaConfigManager.get());
        if (event.getPlayer() != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
        } else {
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
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
