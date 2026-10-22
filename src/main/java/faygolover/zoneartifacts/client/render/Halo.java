package faygolover.zoneartifacts.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.Breath;
import faygolover.zoneartifacts.client.chem.Gas;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.tesla.ChemCometEntity;
import faygolover.zoneartifacts.tesla.CometEntity;
import faygolover.zoneartifacts.tesla.GraviEntity;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The glow of the air round the fireballs — the Comets, the Tesla: a wide, soft halo (a gaussian per
 * pixel) breathing with the ball, faint enough that the ball stays a dense ball of fire and doesn't
 * turn into a glowing dot. After the Comets themselves (after the weather).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Halo {

    private static final double MAX_DISTANCE = 64.0;

    private Halo() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (LatePass.deferred(event)) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !ModClientConfig.glowHalo()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ShaderInstance shader = ZaShaders.halo();
        if (shader == null) return;
        float partial = event.getPartialTick();
        float time = (mc.level.getGameTime() % 72000L) + partial;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        PoseStack poseStack = Gas.lateStagePose(event);
        Matrix4f m = poseStack.last().pose();

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean any = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof TeslaEntity t) || e instanceof GraviEntity || e instanceof ChemCometEntity) continue;
            if (!t.getState().isVisible() || t.getState() == TeslaEntity.State.SPAWNING) continue;
            Vec3 c = e.getPosition(partial).add(0.0, e.getBbHeight() / 2.0, 0.0);
            if (c.distanceToSqr(cam) > MAX_DISTANCE * MAX_DISTANCE) continue;
            float size = t.getSize();
            float breath = 1.0f + 0.12f * Breath.of(time, e.getId() * 0.37f);
            float r;
            float g;
            float b;
            float strength;
            double radius;
            if (e instanceof CometEntity comet) {
                if (comet.isCold()) {
                    r = 0.35f; g = 0.75f; b = 1.0f;
                } else {
                    r = 1.0f; g = 0.5f; b = 0.15f;
                }
                strength = 0.13f * breath;
                radius = 1.5 * size;
            } else {
                r = 0.55f; g = 0.72f; b = 1.0f;
                strength = 0.11f * breath;
                radius = 1.3 * size;
            }
            radius *= 0.96 + 0.04 * breath;
            quad(buffer, m, c.subtract(cam), left, up, radius, r, g, b, strength);
            any = true;
        }
        if (!any) {
            buffer.end();
            return;
        }
        RenderSystem.setShader(() -> shader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buffer, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, double radius,
                             float r, float g, float b, float a) {
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] k : corners) {
            double x = c.x + (left.x() * k[0] + up.x() * k[1]) * radius;
            double y = c.y + (left.y() * k[0] + up.y() * k[1]) * radius;
            double z = c.z + (left.z() * k[0] + up.z() * k[1]) * radius;
            buffer.vertex(m, (float) x, (float) y, (float) z).uv(k[0], k[1]).color(r, g, b, a).endVertex();
        }
    }
}
