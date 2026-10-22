package faygolover.zoneartifacts.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
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
import faygolover.zoneartifacts.client.razlom.RazlomRenderer;
import faygolover.zoneartifacts.client.rust.RustClient;
import faygolover.zoneartifacts.client.swamp.SwampClient;
import faygolover.zoneartifacts.client.tesla.CometEffectRenderer;
import faygolover.zoneartifacts.client.tesla.CometLateRender;
import faygolover.zoneartifacts.client.tesla.TeslaEffectRenderer;
import faygolover.zoneartifacts.client.tesla.TeslaRouteRenderer;
import faygolover.zoneartifacts.client.thermal.IneyFrostRenderer;
import faygolover.zoneartifacts.client.web.WebClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Drawing the anomalies when a shader pack (Oculus) is on.
 * <p>
 * While a pack draws the world it swaps vanilla's shaders for its own programs: those light our
 * glows like a mob's skin, take a vertex colour per triangle instead of a smooth gradient (soft
 * rims vanish) and leave no picture for the air distortion to copy; a mod's own shader is blanked
 * outright. So with a pack the anomalies sit the world pass out: each world stage only notes its
 * matrices and fog, and at {@code AFTER_LEVEL} — after the pack's final pass, before the hand and
 * the HUD — every anomaly renderer is called again for each stage in turn, on the finished frame,
 * with vanilla's (and our own) shaders, depth-tested against the world's depth (the hand's
 * included). Only the Rust's moss (a ready vertex buffer), the Hedgehog and the Fluff (block entities)
 * stay in the world pass, lit and shadowed by the pack; the poppies and the debris come here too —
 * batched in the pack's own pass they now and then turned up a second time, out of place.
 * <p>
 * Without a pack nothing changes: the stages draw as they come.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LatePass {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The renderers drawn again in the late pass, in the order Forge calls them in one stage
     *  (normal priority first, then the air distortion (low), then the soft dark shapes (lowest)). */
    private static final List<Consumer<RenderLevelStageEvent>> RENDERERS = List.of(
            AnomalyArcRenderer::onRenderLevelStage,
            AnomalyHighlightRenderer::onRenderLevelStage,
            ElectrifyRenderer::onRenderLevelStage,
            AmoebaClient::onRenderLevelStage,
            Gas::onRenderLevelStage,
            FantomClient::onRenderLevelStage,
            FireflyClient::onRenderLevelStage,
            GoreClient::onRenderLevelStage,
            GravityRenderer::onRenderLevelStage,
            KamertonClient::onRenderLevelStage,
            KhlopushkaClient::onRenderLevelStage,
            KiselClient::onRenderLevelStage,
            RazlomRenderer::onRenderLevelStage,
            RustClient::onRenderLevelStage,
            SwampClient::onRenderLevelStage,
            CometEffectRenderer::onRenderLevelStage,
            CometLateRender::onRenderLevelStage,
            TeslaEffectRenderer::onRenderLevelStage,
            TeslaRouteRenderer::onRenderLevelStage,
            IneyFrostRenderer::onRenderLevelStage,
            WebClient::onRenderLevelStage,
            faygolover.zoneartifacts.client.poppy.PoppyClient::onRenderLevelStage,
            Distortion::onRenderLevelStage,
            GravityRenderer::onRenderShapes);

    /** What one world stage looked like: replayed in the late pass. */
    private record Snapshot(RenderLevelStageEvent.Stage stage, LevelRenderer levelRenderer, Matrix4f pose, Matrix4f projection,
                            Matrix4f modelView, int renderTick, float partialTick, Camera camera, Frustum frustum,
                            float fogStart, float fogEnd, float[] fogColor, com.mojang.blaze3d.shaders.FogShape fogShape) {
    }

    /** An anomaly entity's drawing, put off to the late pass. */
    public interface EntityDraw {
        void draw(PoseStack poseStack, MultiBufferSource.BufferSource buffers);
    }

    private record DeferredEntity(Entity entity, float partialTick, EntityDraw draw) {
    }

    /** The replayed stage events — told apart from the real ones by their class. */
    private static final class Replay extends RenderLevelStageEvent {
        Replay(Snapshot s) {
            super(s.stage(), s.levelRenderer(), pose(s.pose()), new Matrix4f(s.projection()), s.renderTick(), s.partialTick(),
                    s.camera(), s.frustum());
        }

        private static PoseStack pose(Matrix4f m) {
            PoseStack poseStack = new PoseStack();
            poseStack.last().pose().set(m);
            return poseStack;
        }
    }

    private static final List<Snapshot> SNAPSHOTS = new ArrayList<>();
    private static final List<DeferredEntity> ENTITIES = new ArrayList<>();
    /** This frame: a shader pack draws the world. */
    private static boolean active;
    /** The deferred entities are being drawn (their renderers draw for real now). */
    private static boolean drawingEntities;

    @Nullable
    private static Object irisApi;
    @Nullable
    private static Method packInUse;
    private static boolean probed;

    private LatePass() {
    }

    /** True while a shader pack (Oculus / Iris) draws the world. */
    public static boolean shaderPack() {
        if (!probed) {
            probed = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                packInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // No Oculus.
            }
        }
        if (packInUse == null) return false;
        try {
            return (Boolean) packInUse.invoke(irisApi);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Zone Artifacts: can't ask Oculus whether a shader pack is on", e);
            packInUse = null;
            return false;
        }
    }

    /** Whether a shader pack is on this frame (the late pass will draw). */
    public static boolean active() {
        return active;
    }

    /**
     * True for a real world stage while a shader pack is on: the renderer should draw nothing now —
     * it is called again for this stage in the late pass.
     */
    public static boolean deferred(RenderLevelStageEvent event) {
        return active && !(event instanceof Replay);
    }

    /** True for a stage replayed in the late pass (solid parts, drawn in the world pass, are skipped). */
    public static boolean replayed(RenderLevelStageEvent event) {
        return event instanceof Replay;
    }

    /**
     * For an anomaly entity's renderer: with a shader pack on, notes the drawing down for the late
     * pass and returns true (draw nothing now). The pose given to {@code draw} is at the entity's
     * feet, like the renderer's own; when it is called, this returns false (draw for real).
     */
    public static boolean deferEntity(Entity entity, float partialTick, EntityDraw draw) {
        if (!active || drawingEntities) return false;
        ENTITIES.add(new DeferredEntity(entity, partialTick, draw));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event instanceof Replay) return;
        RenderLevelStageEvent.Stage stage = event.getStage();
        // The terrain's fog for our shaders (pack or not), as it is while the entities are drawn
        // (a vanilla stage: Embeddium draws the terrain itself).
        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) ZaShaders.syncFog();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            SNAPSHOTS.clear();
            ENTITIES.clear();
            active = shaderPack();
        }
        if (!active) return;
        if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            replay();
            return;
        }
        SNAPSHOTS.add(new Snapshot(stage, event.getLevelRenderer(), new Matrix4f(event.getPoseStack().last().pose()),
                new Matrix4f(RenderSystem.getProjectionMatrix()), new Matrix4f(RenderSystem.getModelViewMatrix()),
                event.getRenderTick(), event.getPartialTick(), event.getCamera(), event.getFrustum(),
                RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogColor().clone(),
                RenderSystem.getShaderFogShape()));
    }

    private static void replay() {
        if (SNAPSHOTS.isEmpty()) {
            ENTITIES.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.getMainRenderTarget().bindWrite(true);
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        float oldFogStart = RenderSystem.getShaderFogStart();
        float oldFogEnd = RenderSystem.getShaderFogEnd();
        float[] oldFogColor = RenderSystem.getShaderFogColor().clone();
        com.mojang.blaze3d.shaders.FogShape oldFogShape = RenderSystem.getShaderFogShape();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        try {
            // Inside a Haze or a Dusk: their fog over the pack's frame first (the pack ignores vanilla's).
            if (ScreenFog.needed(SNAPSHOTS.get(0).partialTick())) {
                Snapshot world = SNAPSHOTS.get(SNAPSHOTS.size() - 1);
                ScreenFog.draw(world.projection());
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
            }
            for (Snapshot s : SNAPSHOTS) {
                RenderSystem.setProjectionMatrix(s.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
                modelView.setIdentity();
                modelView.mulPoseMatrix(s.modelView());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderFogStart(s.fogStart());
                RenderSystem.setShaderFogEnd(s.fogEnd());
                RenderSystem.setShaderFogColor(s.fogColor()[0], s.fogColor()[1], s.fogColor()[2], s.fogColor()[3]);
                RenderSystem.setShaderFogShape(s.fogShape());
                if (s.stage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) drawEntities(s);
                for (Consumer<RenderLevelStageEvent> renderer : RENDERERS) {
                    try {
                        renderer.accept(new Replay(s));
                    } catch (RuntimeException e) {
                        LOGGER.error("Zone Artifacts: late pass renderer failed", e);
                    }
                }
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
            RenderSystem.setShaderFogStart(oldFogStart);
            RenderSystem.setShaderFogEnd(oldFogEnd);
            RenderSystem.setShaderFogColor(oldFogColor[0], oldFogColor[1], oldFogColor[2], oldFogColor[3]);
            RenderSystem.setShaderFogShape(oldFogShape);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            SNAPSHOTS.clear();
            ENTITIES.clear();
        }
    }

    /** The anomaly entities, at the point vanilla drew entities. */
    private static void drawEntities(Snapshot s) {
        if (ENTITIES.isEmpty()) return;
        Vec3 cam = s.camera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack poseStack = Replay.pose(s.pose());
        drawingEntities = true;
        try {
        for (DeferredEntity d : ENTITIES) {
            if (d.entity().isRemoved()) continue;
            Vec3 p = d.entity().getPosition(d.partialTick());
            poseStack.pushPose();
            poseStack.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            try {
                d.draw().draw(poseStack, buffers);
            } catch (RuntimeException e) {
                LOGGER.error("Zone Artifacts: late pass entity drawing failed", e);
            }
            poseStack.popPose();
        }
        } finally {
            drawingEntities = false;
        }
        buffers.endBatch();
    }
}
