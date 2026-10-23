package faygolover.zoneartifacts.client.render;

import faygolover.flrender.render.ScreenPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import faygolover.zoneartifacts.client.dymka.DymkaClient;
import faygolover.zoneartifacts.client.sumrak.SumrakClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * The Haze's and the Dusk's fog with a shader pack on. A pack works out its own fog and ignores
 * vanilla's fog distances, so from inside neither closed in at all. In the late pass ({@code faygolover.flrender.render.LatePass}), before
 * the anomalies: vanilla's linear fog (the same distances and colour the zones set) laid over the
 * pack's finished frame from its depth; the hand stays clear. Without a pack vanilla's fog does it.
 */
final class ScreenFog {

    /** With a pack the hand sits in the middle of the depth range (0.44..0.56): nearer than this is the hand. */
    private static final float HAND_DEPTH = 0.75f;

    @Nullable
    private static TextureTarget layer;

    private ScreenFog() {
    }

    /** Whether to draw it now: inside a Haze or a Dusk. */
    static boolean needed(float partial) {
        return DymkaClient.inside(partial) > 0.001f || SumrakClient.inside(partial) > 0.001f;
    }

    /** Lays the fog over the main target ({@code projection}: the world's, this frame). */
    static void draw(Matrix4f projection) {
        ShaderInstance shader = ZaShaders.screenFog();
        if (shader == null) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        // Into a layer of its own first: the depth can't be read while its own framebuffer is drawn to.
        layer = ScreenPass.fit(layer, main);
        layer.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        layer.clear(Minecraft.ON_OSX);
        float[] c = ZaShaders.fogColor();
        shader.setSampler("DepthSampler", main.getDepthTextureId());
        shader.safeGetUniform("InvProjMat").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("ZaFogStart").set(ZaShaders.fogStart());
        shader.safeGetUniform("ZaFogEnd").set(ZaShaders.fogEnd());
        shader.safeGetUniform("ZaFogColor").set(c[0], c[1], c[2], 1.0f);
        shader.safeGetUniform("HandDepth").set(HAND_DEPTH);
        ScreenPass.fullscreen(layer, shader, false);

        // Then over the frame.
        ShaderInstance blit = GameRenderer.getPositionTexShader();
        if (blit != null) {
            blit.setSampler("Sampler0", layer.getColorTextureId());
            RenderSystem.setShaderTexture(0, layer.getColorTextureId());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            ScreenPass.fullscreen(main, blit, true);
            RenderSystem.disableBlend();
        }
        main.bindWrite(true);
        RenderSystem.enableDepthTest();
    }
}
