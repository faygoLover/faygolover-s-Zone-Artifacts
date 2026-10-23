package faygolover.zoneartifacts.client.render;

import faygolover.flrender.render.LatePass;
import faygolover.flrender.render.SceneDepth;
import faygolover.flrender.render.WorldLight;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.AnomalyArcRenderer;
import faygolover.zoneartifacts.client.AnomalyHighlightRenderer;
import faygolover.zoneartifacts.client.ElectrifyRenderer;
import faygolover.zoneartifacts.client.amoeba.AmoebaClient;
import faygolover.zoneartifacts.client.chem.Gas;
import faygolover.zoneartifacts.client.distortion.Distortion;
import faygolover.zoneartifacts.client.fantom.FantomClient;
import faygolover.zoneartifacts.client.firefly.FireflyClient;
import faygolover.zoneartifacts.client.gravity.GoreClient;
import faygolover.zoneartifacts.client.gravity.GravityRenderer;
import faygolover.zoneartifacts.client.kamerton.KamertonClient;
import faygolover.zoneartifacts.client.khlopushka.KhlopushkaClient;
import faygolover.zoneartifacts.client.kisel.KiselClient;
import faygolover.zoneartifacts.client.poppy.PoppyClient;
import faygolover.zoneartifacts.client.razlom.RazlomRenderer;
import faygolover.zoneartifacts.client.rust.RustClient;
import faygolover.zoneartifacts.client.swamp.SwampClient;
import faygolover.zoneartifacts.client.tesla.CometEffectRenderer;
import faygolover.zoneartifacts.client.tesla.CometLateRender;
import faygolover.zoneartifacts.client.tesla.TeslaEffectRenderer;
import faygolover.zoneartifacts.client.tesla.TeslaRouteRenderer;
import faygolover.zoneartifacts.client.thermal.IneyFrostRenderer;
import faygolover.zoneartifacts.client.web.WebClient;
import faygolover.zoneartifacts.config.ModClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The anomalies on FL Render's shared passes.
 * <p>
 * With a shader pack on, FL Render's {@link LatePass} replays the world stages after the pack's final
 * pass; every anomaly renderer below is drawn again there (each skips the real stage with
 * {@code LatePass.deferred}). Only the Rust's moss (a ready vertex buffer), the Hedgehog and the Fluff
 * (block entities) stay in the world pass, lit and shadowed by the pack.
 * <p>
 * Once a frame, when the world's frame is ready (after the entities without a pack, at the start of the
 * late pass with one): FL Render packs the depth and draws the anomalies' light ({@link ZaLights}); then
 * our shaders learn the frame and — inside a Haze or a Dusk, over a pack's frame — their fog is laid over
 * it (the pack ignores vanilla's).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ZaLatePass {

    /** How far before the world behind them soft things have melted away, blocks. */
    private static final float SOFT_RANGE = 0.7f;

    private ZaLatePass() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ZaTechniques.init();
        // In the order Forge calls them in one stage (normal priority first, then the air distortion (low),
        // then the soft dark shapes (lowest)).
        LatePass.register(AnomalyArcRenderer::onRenderLevelStage);
        LatePass.register(AnomalyHighlightRenderer::onRenderLevelStage);
        LatePass.register(ElectrifyRenderer::onRenderLevelStage);
        LatePass.register(AmoebaClient::onRenderLevelStage);
        LatePass.register(Gas::onRenderLevelStage);
        LatePass.register(FantomClient::onRenderLevelStage);
        LatePass.register(FireflyClient::onRenderLevelStage);
        LatePass.register(GoreClient::onRenderLevelStage);
        LatePass.register(GravityRenderer::onRenderLevelStage);
        LatePass.register(KamertonClient::onRenderLevelStage);
        LatePass.register(KhlopushkaClient::onRenderLevelStage);
        LatePass.register(KiselClient::onRenderLevelStage);
        LatePass.register(RazlomRenderer::onRenderLevelStage);
        LatePass.register(RustClient::onRenderLevelStage);
        LatePass.register(SwampClient::onRenderLevelStage);
        LatePass.register(CometEffectRenderer::onRenderLevelStage);
        LatePass.register(Smoke::onRenderLevelStage);
        LatePass.register(CometLateRender::onRenderLevelStage);
        LatePass.register(Halo::onRenderLevelStage);
        LatePass.register(TeslaEffectRenderer::onRenderLevelStage);
        LatePass.register(TeslaRouteRenderer::onRenderLevelStage);
        LatePass.register(IneyFrostRenderer::onRenderLevelStage);
        LatePass.register(WebClient::onRenderLevelStage);
        LatePass.register(PoppyClient::onRenderLevelStage);
        LatePass.register(Distortion::onRenderLevelStage);
        LatePass.register(GravityRenderer::onRenderShapes);

        SceneDepth.need(() -> ModClientConfig.softEdges() || ModClientConfig.volumetricSmoke());
        WorldLight.provider(ZaLights::collect);
        LatePass.onFrameReady((partial, pack, projection) -> {
            ZaShaders.syncScene(SceneDepth.texture(), SceneDepth.invProjection(), SceneDepth.width(), SceneDepth.height(),
                    SceneDepth.ready() && ModClientConfig.softEdges() ? SOFT_RANGE : 0.0f, ModClientConfig.dithering() ? 1.5f : 0.0f);
            if (pack && ScreenFog.needed(partial)) ScreenFog.draw(projection);
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ZaLatePass::onRenderLevelStage);
    }

    /**
     * The terrain's fog for our shaders (pack or not), as it is while the entities are drawn (a vanilla
     * stage: Embeddium draws the terrain itself).
     */
    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES && !LatePass.replayed(event)) ZaShaders.syncFog();
    }
}
