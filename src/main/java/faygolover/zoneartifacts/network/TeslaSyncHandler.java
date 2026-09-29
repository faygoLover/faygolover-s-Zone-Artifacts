package faygolover.zoneartifacts.network;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Sends every loaded Tesla type's visual/sound config to a player once, on login — unlike {@code
 * AnomalySyncHandler}, there's no per-dimension resend, because none of it (the ball, the bump, the
 * idle sound) varies by dimension; a live {@code TeslaEntity}'s actual position and existence are
 * ordinary vanilla entity-tracker traffic that need no packet of ours at all.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class TeslaSyncHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), SyncTeslaTypesPacket.ofAllLoadedTypes());
        }
    }

    private TeslaSyncHandler() {
    }
}
