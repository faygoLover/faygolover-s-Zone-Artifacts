package faygolover.zoneartifacts.client.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus client setup: every entity type needs a renderer registered, or the client crashes
 *  the moment one is sent to it. */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TeslaClientSetup {

    private TeslaClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TESLA.get(), TeslaRenderer::new);
        event.registerEntityRenderer(ModEntities.COMET.get(), CometRenderer::new);
    }
}
