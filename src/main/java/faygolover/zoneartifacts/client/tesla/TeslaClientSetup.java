package faygolover.zoneartifacts.client.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.registry.ModEntities;
import faygolover.zoneartifacts.tesla.CometEntity;
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
        event.<CometEntity>registerEntityRenderer(ModEntities.COLD_COMET.get(), CometRenderer::new);
        event.registerEntityRenderer(ModEntities.CHEM_COMET.get(), faygolover.zoneartifacts.client.chem.ChemCometRenderer::new);
        event.registerEntityRenderer(ModEntities.GRAVI.get(), faygolover.zoneartifacts.client.gravi.GraviRenderer::new);
        event.registerEntityRenderer(ModEntities.BUBBLE.get(), faygolover.zoneartifacts.client.bubbles.BubbleRenderer::new);
        event.registerBlockEntityRenderer(faygolover.zoneartifacts.registry.ModBlockEntities.PUKH.get(),
                faygolover.zoneartifacts.client.pukh.PukhRenderer::new);
        event.registerBlockEntityRenderer(faygolover.zoneartifacts.registry.ModBlockEntities.EZHIK.get(),
                faygolover.zoneartifacts.client.ezhik.EzhikRenderer::new);
    }
}
