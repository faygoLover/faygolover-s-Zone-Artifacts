package faygolover.zoneartifacts.client.gravity;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.anomaly.Gravity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Voronka's refraction, without shaders of our own: the frame drawn so far is copied into a
 * texture, and a disc facing the camera is drawn over the anomaly with that copy — but each point
 * of the disc shows the picture from a little further out, so what's behind looks drawn in towards
 * the center (a gravitational lens). The edge of the disc shows exactly what's there, so it has no
 * outline.
 * <ul>
 *     <li>ready: a barely visible, slowly breathing pinch, fading in after the cooldown;</li>
 *     <li>pulling: the pinch deepens, ripples of distorted air run in to the center, the picture
 *     twists and goes blurry;</li>
 *     <li>the tear: a ring of distortion rushing out;</li>
 *     <li>on cooldown: nothing.</li>
 * </ul>
 */
final class VoronkaLens {

    private static final int RINGS = 14;
    private static final int SEGMENTS = 40;
    static final float RELEASE_TICKS = 20.0f;

    @Nullable
    private static TextureTarget copy;

    private VoronkaLens() {
    }

    /** One lens: disc radius; pinch (0 none … <3); twist (radians at the center); ripple amplitude
     *  and phase; outward-ring bump (amplitude, where); blur (screen fraction); opacity. */
    private record Lens(Vec3 c, double radius, double pinch, double twist, double ripple, double ripplePhase,
                        double bump, double bumpAt, double blur, float alpha) {
    }

    static void render(Minecraft mc, Vec3 cam, Matrix4f pose, long now, float partial) {
        float time = (now % 72000L) + partial;
        List<Lens> lenses = new ArrayList<>();
        for (GravityClientHandler.State state : GravityClientHandler.states()) {
            if (state.kind() != GravityClientHandler.Kind.VORONKA) continue;
            Lens lens = lensFor(state, now, partial, time, cam);
            if (lens != null) lenses.add(lens);
        }
        if (lenses.isEmpty()) return;

        Matrix4f toScreen = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose);
        if (!grab(mc)) return;

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Lens lens : lenses) {
            disc(buffer, pose, toScreen, cam, lens, 0.0f, 0.0f, 1.0f);
            if (lens.blur() > 1.0E-4) {
                // Blur ("the air goes cloudy"): the same picture a few times, slightly shifted.
                float b = (float) lens.blur();
                disc(buffer, pose, toScreen, cam, lens, b, 0.0f, 0.3f);
                disc(buffer, pose, toScreen, cam, lens, -b, 0.0f, 0.3f);
                disc(buffer, pose, toScreen, cam, lens, 0.0f, b, 0.3f);
                disc(buffer, pose, toScreen, cam, lens, 0.0f, -b, 0.3f);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    @Nullable
    private static Lens lensFor(GravityClientHandler.State state, long now, float partial, float time, Vec3 cam) {
        Vec3 c = state.center();
        double half = state.entry().size() * 0.5;
        double reach = Gravity.reach(state.entry().size());
        float release = (now - state.releaseTick() + partial) / RELEASE_TICKS;
        double radius;
        double pinch = 0.0;
        double twist = 0.0;
        double ripple = 0.0;
        double ripplePhase = 0.0;
        double bump = 0.0;
        double blur = 0.0;
        float alpha;
        if (state.active()) {
            float t = state.progress(now, partial);
            // Opens out from the resting size over the first moments, then closes in.
            double open = Math.min(1.0, t / 0.15);
            radius = Mth.lerp(open, half * 1.1, Math.max(half, reach * (1.0 - 0.45 * t)));
            pinch = Mth.lerp(open, 0.2, 0.3 + 1.5 * t);
            twist = (0.35 + 0.1 * Math.sin(time * 0.15)) * t;
            ripple = 0.03 + 0.05 * t;
            ripplePhase = time * (0.05 + 0.07 * t);
            blur = 0.0015 + 0.0055 * t;
            alpha = 1.0f;
        } else if (release >= 0.0f && release < 1.0f) {
            float e = 1.0f - (1.0f - release) * (1.0f - release);
            radius = reach * (0.5 + 1.3 * e);
            bump = -0.12 * (1.0f - release);
            blur = 0.004 * (1.0f - release);
            alpha = 1.0f - release * release;
        } else {
            float ready = state.readiness(now, partial);
            if (ready <= 0.0f) return null;
            radius = half * 1.1;
            pinch = 0.2 * ready * (1.0 + 0.25 * Math.sin(time * 0.04));
            ripple = 0.01 * ready;
            ripplePhase = time * 0.02;
            alpha = ready;
        }
        // The disc must stay in front of the camera: shrink it when you're close (or inside).
        double dist = cam.distanceTo(c);
        radius = Math.min(radius, dist * 0.7);
        if (radius < 0.15) return null;
        // Keep the blur the same size in the world, roughly.
        blur *= Mth.clamp(6.0 / Math.max(0.1, dist), 0.3, 1.5);
        return new Lens(c, radius, pinch, twist, ripple, ripplePhase, bump, 0.78, blur, alpha);
    }

    /** One pass of the lens disc; {@code du, dv}: extra screen offset (blur taps). */
    private static void disc(BufferBuilder buffer, Matrix4f pose, Matrix4f toScreen, Vec3 cam, Lens lens,
                             float du, float dv, float alphaScale) {
        Vec3 c = lens.c();
        Vec3 toCam = cam.subtract(c);
        if (toCam.lengthSqr() < 1.0E-6) return;
        toCam = toCam.normalize();
        Vec3 up = Math.abs(toCam.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = toCam.cross(up).normalize();
        Vec3 upOnPlane = right.cross(toCam).normalize();

        // Positions (relative to the camera) and screen UVs of the displaced samples, per grid point.
        float[][] px = new float[RINGS + 1][SEGMENTS + 1];
        float[][] py = new float[RINGS + 1][SEGMENTS + 1];
        float[][] pz = new float[RINGS + 1][SEGMENTS + 1];
        float[][] us = new float[RINGS + 1][SEGMENTS + 1];
        float[][] vs = new float[RINGS + 1][SEGMENTS + 1];
        float[] alphas = new float[RINGS + 1];
        Vector4f tmp = new Vector4f();
        double r0 = lens.radius();
        for (int i = 0; i <= RINGS; i++) {
            double x = i / (double) RINGS;
            double r = x * r0;
            double inner = (1.0 - x) * (1.0 - x);
            double s = r * (1.0 + lens.pinch() * inner)
                    + r0 * lens.ripple() * Math.sin(Math.PI * 2.0 * (3.0 * x + lens.ripplePhase())) * 4.0 * x * (1.0 - x)
                    + r0 * lens.bump() * Math.exp(-Math.pow((x - lens.bumpAt()) / 0.12, 2.0)) * (1.0 - Math.pow(x, 8.0));
            double spin = lens.twist() * inner;
            float edge = (float) Mth.clamp((x - 0.72) / 0.28, 0.0, 1.0);
            alphas[i] = lens.alpha() * alphaScale * (1.0f - edge * edge * (3.0f - 2.0f * edge));
            for (int j = 0; j <= SEGMENTS; j++) {
                double a = Math.PI * 2.0 * j / SEGMENTS;
                Vec3 p = c.add(right.scale(Math.cos(a) * r)).add(upOnPlane.scale(Math.sin(a) * r)).subtract(cam);
                px[i][j] = (float) p.x;
                py[i][j] = (float) p.y;
                pz[i][j] = (float) p.z;
                double as = a + spin;
                Vec3 q = c.add(right.scale(Math.cos(as) * s)).add(upOnPlane.scale(Math.sin(as) * s)).subtract(cam);
                tmp.set((float) q.x, (float) q.y, (float) q.z, 1.0f);
                toScreen.transform(tmp);
                float w = Math.max(1.0E-4f, tmp.w);
                us[i][j] = tmp.x / w * 0.5f + 0.5f + du;
                vs[i][j] = tmp.y / w * 0.5f + 0.5f + dv;
            }
        }
        for (int i = 0; i < RINGS; i++) {
            if (alphas[i] <= 0.004f && alphas[i + 1] <= 0.004f) continue;
            for (int j = 0; j < SEGMENTS; j++) {
                vertex(buffer, pose, px, py, pz, us, vs, alphas, i, j);
                vertex(buffer, pose, px, py, pz, us, vs, alphas, i + 1, j);
                vertex(buffer, pose, px, py, pz, us, vs, alphas, i + 1, j + 1);
                vertex(buffer, pose, px, py, pz, us, vs, alphas, i, j + 1);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, float[][] px, float[][] py, float[][] pz,
                               float[][] us, float[][] vs, float[] alphas, int i, int j) {
        buffer.vertex(pose, px[i][j], py[i][j], pz[i][j]).uv(us[i][j], vs[i][j])
                .color(1.0f, 1.0f, 1.0f, alphas[i]).endVertex();
    }

    /** Copies the main frame into {@link #copy} (alpha forced to 1), leaving the GL bindings as they were. */
    private static boolean grab(Minecraft mc) {
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
        // The frame's alpha isn't always 1 (the sky, for one): make the copy opaque.
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
