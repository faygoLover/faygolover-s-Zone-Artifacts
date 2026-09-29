package faygolover.zoneartifacts.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.dymka.DymkaClient;
import faygolover.zoneartifacts.client.poppy.PoppyClient;
import faygolover.zoneartifacts.client.psi.PsiClient;
import faygolover.zoneartifacts.client.sumrak.SumrakClient;
import faygolover.zoneartifacts.client.swamp.SwampClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * What the senses-dulling anomalies do to the picture:
 * <ul>
 *     <li>fog — the Haze closes it in to a few blocks of grey, the Dusk to black at arm's length;</li>
 *     <li>under everything else on screen: the psi zone's swimming, split, blurred sight (the frame
 *     copied and drawn back distorted, colour channels apart, smeared — no shaders of our own), the
 *     black of the mud or the Dusk, and the poppy field's heavy eyelids.</li>
 * </ul>
 */
public final class ScreenFx {

    private static TextureTarget copy;

    private ScreenFx() {
    }

    // ---- fog ------------------------------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Fog {

        private Fog() {
        }

        @SubscribeEvent
        public static void onRenderFog(ViewportEvent.RenderFog event) {
            float partial = (float) event.getPartialTick();
            float haze = DymkaClient.inside(partial);
            float dusk = SumrakClient.inside(partial);
            if (haze <= 0.001f && dusk <= 0.001f) return;
            float far = event.getFarPlaneDistance();
            float near = event.getNearPlaneDistance();
            if (haze > 0.001f) {
                float want = DymkaClient.visibility();
                far = Math.min(far, Mth.lerp(haze, event.getFarPlaneDistance(), want));
                near = Math.min(near, Mth.lerp(haze, event.getNearPlaneDistance(), -want * 0.3f));
            }
            if (dusk > 0.001f) {
                far = Math.min(far, Mth.lerp(dusk, event.getFarPlaneDistance(), 1.2f));
                near = Math.min(near, Mth.lerp(dusk, event.getNearPlaneDistance(), -0.5f));
            }
            event.setFarPlaneDistance(far);
            event.setNearPlaneDistance(near);
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onFogColor(ViewportEvent.ComputeFogColor event) {
            float partial = (float) event.getPartialTick();
            float haze = DymkaClient.inside(partial);
            float dusk = SumrakClient.inside(partial);
            if (haze <= 0.001f && dusk <= 0.001f) return;
            float r = event.getRed();
            float g = event.getGreen();
            float b = event.getBlue();
            if (haze > 0.001f) {
                float light = DymkaClient.fogLight();
                r = Mth.lerp(haze, r, 0.62f * light);
                g = Mth.lerp(haze, g, 0.64f * light);
                b = Mth.lerp(haze, b, 0.66f * light);
            }
            if (dusk > 0.001f) {
                r = Mth.lerp(dusk, r, 0.01f);
                g = Mth.lerp(dusk, g, 0.01f);
                b = Mth.lerp(dusk, b, 0.012f);
            }
            event.setRed(r);
            event.setGreen(g);
            event.setBlue(b);
        }
    }

    // ---- the overlay ----------------------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("senses", (gui, graphics, partialTick, width, height) -> draw(graphics, partialTick, width, height));
        }
    }

    private static void draw(GuiGraphics graphics, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float time = (mc.level.getGameTime() % 72000L) + partial;

        float psi = PsiClient.strength(partial);
        if (psi > 0.004f) psi(graphics, psi, PsiClient.wave(partial), time, width, height);

        float black = Math.max(SwampClient.darkness(partial), SumrakClient.inside(partial) * 0.88f);
        black = Math.max(black, PoppyClient.blackout(partial));
        if (black > 0.004f) {
            graphics.fill(0, 0, width, height, argb(black, 0, 0, 0));
        }
        float flash = faygolover.zoneartifacts.client.khlopushka.KhlopushkaClient.flash(partial);
        if (flash > 0.004f) {
            graphics.fill(0, 0, width, height, argb(Math.min(1.0f, flash), 255, 255, 250));
        }
        float lids = PoppyClient.eyelids(partial);
        if (lids > 0.004f && lids < 0.999f) eyelids(graphics, lids, width, height);
    }

    private static int argb(float alpha, int r, int g, int b) {
        return ((int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    /** Heavy eyelids closing from above and below, with a soft edge. */
    private static void eyelids(GuiGraphics graphics, float closed, int width, int height) {
        int half = height / 2;
        int reach = (int) (half * closed);
        int soft = Math.max(4, height / 10);
        graphics.fill(0, 0, width, reach, 0xFF000000);
        graphics.fill(0, height - reach, width, height, 0xFF000000);
        graphics.fillGradient(0, reach, width, reach + soft, 0xFF000000, 0x00000000);
        graphics.fillGradient(0, height - reach - soft, width, height - reach, 0x00000000, 0xFF000000);
        // Everything dims as they close.
        graphics.fill(0, 0, width, height, argb(0.5f * closed, 0, 0, 0));
    }

    /**
     * The psi zone's sight: the frame wobbles like hot air, its colours come apart towards the edges,
     * and in waves it smears. {@code s} 0..1 is the strength, {@code wave} 0..1 the current wave.
     */
    private static void psi(GuiGraphics graphics, float s, float wave, float time, int width, int height) {
        if (!grab()) return;
        Matrix4f m = graphics.pose().last().pose();
        float wobble = 0.004f * s + 0.01f * s * wave;
        float split = 0.003f * s + 0.012f * s * wave;
        float blur = 0.004f * s * (0.3f + wave);

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        // Whole picture, wobbling.
        quadGrid(m, width, height, time, wobble, 0.0f, 0.0f, 0.0f, 1.0f);
        // Red outwards, blue inwards.
        RenderSystem.colorMask(true, false, false, false);
        quadGrid(m, width, height, time, wobble, split, 0.0f, 0.0f, 1.0f);
        RenderSystem.colorMask(false, false, true, false);
        quadGrid(m, width, height, time, wobble, -split, 0.0f, 0.0f, 1.0f);
        RenderSystem.colorMask(true, true, true, true);
        // Smear.
        if (blur > 1.0E-4f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float a = Mth.clamp(0.22f * (s * (0.4f + wave)), 0.0f, 0.3f);
            quadGrid(m, width, height, time, wobble, 0.0f, blur, 0.0f, a);
            quadGrid(m, width, height, time, wobble, 0.0f, -blur, 0.0f, a);
            quadGrid(m, width, height, time, wobble, 0.0f, 0.0f, blur, a);
            quadGrid(m, width, height, time, wobble, 0.0f, 0.0f, -blur, a);
            RenderSystem.disableBlend();
        }
        RenderSystem.enableDepthTest();
    }

    private static void quadGrid(Matrix4f m, int width, int height, float time, float wobble, float split,
                                 float du, float dv, float alpha) {
        int cols = 24;
        int rows = 14;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                vertex(buffer, m, j, i, cols, rows, width, height, time, wobble, split, du, dv, alpha);
                vertex(buffer, m, j, i + 1, cols, rows, width, height, time, wobble, split, du, dv, alpha);
                vertex(buffer, m, j + 1, i + 1, cols, rows, width, height, time, wobble, split, du, dv, alpha);
                vertex(buffer, m, j + 1, i, cols, rows, width, height, time, wobble, split, du, dv, alpha);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void vertex(BufferBuilder buffer, Matrix4f m, int j, int i, int cols, int rows, int width, int height,
                               float time, float wobble, float split, float du, float dv, float alpha) {
        float fx = j / (float) cols;
        float fy = i / (float) rows;
        // Edges stay put (no gaps at the screen's border).
        float edge = Math.min(Math.min(fx, 1.0f - fx), Math.min(fy, 1.0f - fy));
        float keep = Mth.clamp(edge * 8.0f, 0.0f, 1.0f);
        float u = fx + keep * wobble * (Mth.sin(fy * 9.0f + time * 0.07f) + 0.6f * Mth.sin(fx * 13.0f - time * 0.05f));
        float v = fy + keep * wobble * (Mth.sin(fx * 8.0f - time * 0.06f) + 0.6f * Mth.sin(fy * 11.0f + time * 0.08f));
        // Colour split: radial from the middle, stronger to the edges.
        u = 0.5f + (u - 0.5f) * (1.0f - split) + du;
        v = 0.5f + (v - 0.5f) * (1.0f - split) + dv;
        buffer.vertex(m, fx * width, fy * height, 0.0f).uv(u, 1.0f - v).color(1.0f, 1.0f, 1.0f, alpha).endVertex();
    }

    /** Copies the frame drawn so far into {@link #copy}, alpha forced to 1. */
    private static boolean grab() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return false;
        int prevRead = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (copy == null) {
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != main.width || copy.height != main.height) {
            copy.resize(main.width, main.height, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, copy.frameBufferId);
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.colorMask(true, true, true, true);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        RenderSystem.viewport(0, 0, main.width, main.height);
        return true;
    }
}
