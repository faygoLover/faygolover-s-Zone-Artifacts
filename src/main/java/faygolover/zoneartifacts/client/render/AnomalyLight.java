package faygolover.zoneartifacts.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.client.kisel.KiselClient;
import faygolover.zoneartifacts.client.thermal.ThermalClientHandler;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.tesla.ChemCometEntity;
import faygolover.zoneartifacts.tesla.CometEntity;
import faygolover.zoneartifacts.tesla.GraviEntity;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The light burning and glowing anomalies cast on the world round them — the Comets, the Tesla,
 * the Kisel's puddles, the Zharka at work — for the picture only (the world's light levels don't
 * change). Once a frame, right after the entities and before any anomaly is drawn (lit afterwards,
 * the fireballs themselves were brightened up to white); with a shader pack on its finished frame,
 * before the anomalies of the {@link LatePass}. Per pixel from the depth: up to 8 nearest lights, each brightening the surfaces
 * in its reach in its own colour, the more the more they face it. The hand isn't lit (without a pack
 * it's drawn after this; with one, its depth gives it away).
 */
public final class AnomalyLight {

    private static final int MAX_LIGHTS = 8;
    private static final double MAX_DISTANCE = 48.0;
    /** With a pack the hand sits in the middle of the depth range: nearer than this is the hand. */
    private static final float PACK_HAND_DEPTH = 0.75f;

    private record Light(Vec3 pos, float reach, float r, float g, float b, float strength) {
    }

    @Nullable
    private static TextureTarget scene;
    @Nullable
    private static TextureTarget layer;
    @Nullable
    private static Matrix4f view;
    @Nullable
    private static Matrix4f projection;

    private AnomalyLight() {
    }

    /** The world's view and projection this frame (from {@link LatePass}, after the entities). */
    static void capture(Matrix4f pose, Matrix4f proj) {
        view = new Matrix4f(pose);
        projection = new Matrix4f(proj);
    }

    /** Lights up the main target ({@code pack}: a shader pack's finished frame). */
    static void draw(float partial, boolean pack) {
        if (!ModClientConfig.anomalyLight() || view == null || projection == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ShaderInstance shader = ZaShaders.anomalyLight();
        if (shader == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        List<Light> lights = collect(mc, partial, cam);
        if (lights.isEmpty()) return;

        float[] data = new float[MAX_LIGHTS * 8];
        // Brighter at night, gentler by day (by day the sun drowns it out).
        float day = mc.level.getSkyDarken(partial);
        float dim = Mth.clamp(1.3f - 0.8f * day, 0.45f, 1.2f);
        int n = Math.min(MAX_LIGHTS, lights.size());
        for (int i = 0; i < n; i++) {
            Light l = lights.get(i);
            int k = i * 8;
            data[k] = (float) (l.pos.x - cam.x);
            data[k + 1] = (float) (l.pos.y - cam.y);
            data[k + 2] = (float) (l.pos.z - cam.z);
            data[k + 3] = l.reach;
            data[k + 4] = l.r;
            data[k + 5] = l.g;
            data[k + 6] = l.b;
            data[k + 7] = l.strength * dim;
        }

        RenderTarget main = mc.getMainRenderTarget();
        scene = ScreenPass.fit(scene, main);
        layer = ScreenPass.fit(layer, main);
        // The frame as it is, to brighten (it can't be read while drawn to).
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, scene.width, scene.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        layer.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        layer.clear(Minecraft.ON_OSX);

        shader.setSampler("DepthSampler", main.getDepthTextureId());
        shader.setSampler("SceneSampler", scene.getColorTextureId());
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("InvViewMat").set(new Matrix4f(view).invert());
        shader.safeGetUniform("LightData").set(data);
        shader.safeGetUniform("LightCount").set(n);
        shader.safeGetUniform("HandDepth").set(pack ? PACK_HAND_DEPTH : 0.0f);
        ScreenPass.fullscreen(layer, shader, false);

        // The lit pixels over the frame (the rest of the layer is clear).
        ShaderInstance blit = GameRenderer.getPositionTexShader();
        if (blit != null) {
            RenderSystem.setShaderTexture(0, layer.getColorTextureId());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            ScreenPass.fullscreen(main, blit, true);
            RenderSystem.disableBlend();
        }
        main.bindWrite(true);
        RenderSystem.enableDepthTest();
    }

    private static List<Light> collect(Minecraft mc, float partial, Vec3 cam) {
        List<Light> out = new ArrayList<>();
        long time = mc.level.getGameTime();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof TeslaEntity t) || e instanceof GraviEntity || !t.getState().isVisible()) continue;
            Vec3 c = e.getPosition(partial).add(0.0, e.getBbHeight() / 2.0, 0.0);
            float size = t.getSize();
            // Steady light that breathes slowly, like the ball itself — no flicker.
            float flicker = 1.0f + 0.12f * faygolover.zoneartifacts.client.Breath.of(time % 72000L + partial, e.getId() * 0.37f);
            if (e instanceof ChemCometEntity) {
                out.add(new Light(c, 3.5f * size + 1.0f, 0.55f, 0.85f, 0.2f, 0.35f));
            } else if (e instanceof CometEntity comet) {
                // Deep, saturated fire colours (paler ones washed the walls almost white).
                if (comet.isCold()) out.add(new Light(c, 6.5f * size + 1.5f, 0.2f, 0.6f, 1.0f, 1.35f * flicker));
                else out.add(new Light(c, 6.5f * size + 1.5f, 1.0f, 0.42f, 0.07f, 1.5f * flicker));
            } else {
                // The Tesla: a steady blue-white light (it strobed).
                out.add(new Light(c, 5.0f * size + 1.5f, 0.6f, 0.75f, 1.0f, 0.9f * flicker));
            }
        }
        KiselClient.lights((x, y, z, size, act) -> out.add(new Light(new Vec3(x, y, z), (float) (2.0 + size),
                0.45f, 1.0f, 0.25f, 0.3f + 0.35f * act)), partial);
        for (Map.Entry<ThermalClientHandler.Key, ThermalClientHandler.State> e : ThermalClientHandler.states()) {
            ThermalClientHandler.State st = e.getValue();
            if (st.entry() == null || !st.isZharka()) continue;
            float a = st.activity(partial);
            if (a < 0.02f) continue;
            AABB zone = AnomalyGeometry.box(st.entry());
            Vec3 c = new Vec3((zone.minX + zone.maxX) * 0.5, zone.minY + 0.6, (zone.minZ + zone.maxZ) * 0.5);
            out.add(new Light(c, (float) (2.5 + st.entry().size()), 1.0f, 0.5f, 0.15f, 0.9f * a));
        }
        faygolover.zoneartifacts.client.firefly.FireflyClient.lights((pos, size) ->
                out.add(new Light(pos, (float) (size * 0.6 + 2.5), 0.55f, 1.0f, 0.25f, 0.35f)));
        faygolover.zoneartifacts.client.fantom.FantomClient.lights(cam, (pos, size, fade) ->
                out.add(new Light(pos, (float) (size + 3.0), 0.35f, 0.55f, 1.0f, 0.45f * fade)));
        out.removeIf(l -> l.pos.distanceToSqr(cam) > MAX_DISTANCE * MAX_DISTANCE);
        out.sort(Comparator.comparingDouble(l -> l.pos.distanceToSqr(cam)));
        return out;
    }
}
