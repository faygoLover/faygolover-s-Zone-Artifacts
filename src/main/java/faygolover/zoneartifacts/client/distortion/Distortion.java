package faygolover.zoneartifacts.client.distortion;

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
import faygolover.zoneartifacts.config.ModClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Air distortion without shaders of our own: once a frame (only while some patch is in view) the
 * frame drawn so far is copied into a texture, and each patch is drawn as a mesh showing that copy
 * — every point of it shows the picture from a slightly shifted spot, so the air looks bent. The
 * rim of every patch shows exactly what's there, so it has no outline.
 * <ul>
 *     <li>{@link Lens} — round, facing the camera: pinch, twist, ripples, a band (Voronka, Plesh,
 *     Comets, the Razlom's jet);</li>
 *     <li>{@link Haze} — an upright sheet turned to the camera: rising heat shimmer (Zharka, Iney,
 *     the Razlom's flame) or a twisting whirl (Karusel).</li>
 * </ul>
 * Patches come from {@link DistortionSources}. Off with {@code airDistortion = false} in the
 * client config.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Distortion {

    private static final int MAX_PATCHES = 16;
    private static final double MAX_DISTANCE = 48.0;

    @Nullable
    private static TextureTarget copy;

    private Distortion() {
    }

    // ==== patches ==================================================================================

    /** One distorted patch: a grid of points, each with where it's drawn, where it samples the
     *  picture from, and how opaque it is. */
    public abstract static class Patch {
        final Vec3 anchor;
        final double size;
        final double blur;
        /** Colours coming apart through it (red one way, blue the other), in screen fractions. */
        double chroma;

        Patch(Vec3 anchor, double size, double blur) {
            this.anchor = anchor;
            this.size = size;
            this.blur = blur;
        }

        abstract int rows();

        abstract int cols();

        /** Sets up for this camera; false to skip the patch. */
        abstract boolean prepare(Vec3 cam);

        /** Point (i, j): out[0..2] drawn at, out[3..5] sampled from (world), out[6] alpha. */
        abstract void point(int i, int j, double[] out);

        public Patch chroma(double amount) {
            this.chroma = amount;
            return this;
        }
    }

    /** Round, facing the camera. {@code pinch}: shows what's further out, drawing things towards
     *  the center (0 none, under 3); {@code twist}: radians at the center; {@code ripple}: waves
     *  running in (phase grows with time); {@code bump} at {@code bumpAt}: a band of stronger
     *  refraction (negative = magnified). Sizes relative to the radius. */
    public static final class Lens extends Patch {
        private final double radius0;
        private final double pinch;
        private final double twist;
        private final double ripple;
        private final double ripplePhase;
        private final double bump;
        private final double bumpAt;
        private final double bumpWidth;
        private final float alpha;
        private final int rings;
        private final int segments;
        private double radius;
        private Vec3 right;
        private Vec3 up;

        public Lens(Vec3 c, double radius, double pinch, double twist, double ripple, double ripplePhase,
                    double bump, double bumpAt, double bumpWidth, double blur, float alpha, int rings, int segments) {
            super(c, radius, blur);
            this.radius0 = radius;
            this.pinch = pinch;
            this.twist = twist;
            this.ripple = ripple;
            this.ripplePhase = ripplePhase;
            this.bump = bump;
            this.bumpAt = bumpAt;
            this.bumpWidth = bumpWidth;
            this.alpha = alpha;
            this.rings = rings;
            this.segments = segments;
        }

        /** A plain shimmer: ripples only. */
        public static Lens shimmer(Vec3 c, double radius, double ripple, double phase, float alpha) {
            return new Lens(c, radius, 0.0, 0.0, ripple, phase, 0.0, 0.8, 0.1, 0.0, alpha, 10, 24);
        }

        @Override
        int rows() {
            return rings;
        }

        @Override
        int cols() {
            return segments;
        }

        @Override
        boolean prepare(Vec3 cam) {
            Vec3 toCam = cam.subtract(anchor);
            double dist = toCam.length();
            // Must stay in front of the camera: shrink it when you're close (or inside).
            radius = Math.min(radius0, dist * 0.7);
            if (radius < 0.15 || alpha <= 0.004f) return false;
            toCam = toCam.scale(1.0 / dist);
            Vec3 helper = Math.abs(toCam.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            right = toCam.cross(helper).normalize();
            up = right.cross(toCam).normalize();
            return true;
        }

        @Override
        void point(int i, int j, double[] out) {
            double x = i / (double) rings;
            double r = x * radius;
            double inner = (1.0 - x) * (1.0 - x);
            double s = r * (1.0 + pinch * inner)
                    + radius * ripple * Math.sin(Math.PI * 2.0 * (3.0 * x + ripplePhase)) * 4.0 * x * (1.0 - x)
                    + radius * bump * Math.exp(-Math.pow((x - bumpAt) / bumpWidth, 2.0)) * (1.0 - Math.pow(x, 8.0));
            double a = Math.PI * 2.0 * j / segments;
            double as = a + twist * inner;
            Vec3 p = anchor.add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
            Vec3 q = anchor.add(right.scale(Math.cos(as) * s)).add(up.scale(Math.sin(as) * s));
            float edge = (float) Mth.clamp((x - 0.9) / 0.1, 0.0, 1.0);
            set(out, p, q, alpha * (1.0f - edge * edge * (3.0f - 2.0f * edge)));
        }
    }

    /** An upright sheet from {@code base} (bottom middle), turned to the camera around the vertical.
     *  Heat: waves rising through it; whirl: bands swinging sideways, turning. {@code amplitude} in blocks. */
    public static final class Haze extends Patch {
        private final double width;
        private final double height;
        private final double amplitude;
        private final double phase;
        private final double waves;
        private final boolean whirl;
        private final float alpha;
        private final double seed;
        private Vec3 right;

        public Haze(Vec3 base, double width, double height, double amplitude, double phase, double waves,
                    boolean whirl, float alpha, double seed) {
            super(base.add(0.0, height * 0.5, 0.0), Math.max(width, height) * 0.5, 0.0);
            this.width = width;
            this.height = height;
            this.amplitude = amplitude;
            this.phase = phase;
            this.waves = waves;
            this.whirl = whirl;
            this.alpha = alpha;
            this.seed = seed;
        }

        @Override
        int rows() {
            return 12;
        }

        @Override
        int cols() {
            return 8;
        }

        @Override
        boolean prepare(Vec3 cam) {
            double dx = cam.x - anchor.x;
            double dz = cam.z - anchor.z;
            double h = Math.sqrt(dx * dx + dz * dz);
            if (h < width * 0.6 + 0.2 || alpha <= 0.004f || amplitude <= 1.0E-4) return false;
            right = new Vec3(-dz / h, 0.0, dx / h);
            return true;
        }

        @Override
        void point(int i, int j, double[] out) {
            double v = i / (double) rows();          // 0 bottom .. 1 top
            double u = j / (double) cols() * 2.0 - 1.0; // -1 .. 1 across
            Vec3 p = anchor.add(right.scale(u * width * 0.5)).add(0.0, (v - 0.5) * height, 0.0);
            double env = (1.0 - u * u) * Math.sin(Math.PI * Math.min(1.0, v * 1.15));
            double dx;
            double dy;
            if (whirl) {
                double w = Math.PI * 2.0 * (v * waves + phase) + u * Math.PI * 1.5 + seed;
                dx = amplitude * env * Math.sin(w);
                dy = amplitude * 0.25 * env * Math.cos(w * 1.3);
            } else {
                double w = Math.PI * 2.0 * (v * waves - phase) + seed;
                dx = amplitude * env * Math.sin(w + u * 2.1);
                dy = amplitude * 0.4 * env * Math.sin(w * 0.7 + u * 3.3);
            }
            Vec3 q = p.add(right.scale(dx)).add(0.0, dy, 0.0);
            float fu = (float) Mth.clamp((Math.abs(u) - 0.7) / 0.3, 0.0, 1.0);
            float fTop = (float) Mth.clamp((v - 0.75) / 0.25, 0.0, 1.0);
            float fBottom = (float) Mth.clamp(v / 0.08, 0.0, 1.0);
            float a = alpha * (1.0f - fu * fu * (3.0f - 2.0f * fu)) * (1.0f - fTop * fTop * (3.0f - 2.0f * fTop)) * fBottom;
            set(out, p, q, a);
        }
    }

    private static void set(double[] out, Vec3 p, Vec3 q, float alpha) {
        out[0] = p.x;
        out[1] = p.y;
        out[2] = p.z;
        out[3] = q.x;
        out[4] = q.y;
        out[5] = q.z;
        out[6] = alpha;
    }

    // ==== drawing ==================================================================================

    /** After the translucent layer (so water and glass are in the picture), after the anomalies'
     *  debris (normal priority) and before their dark shapes (lowest). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ModClientConfig.airDistortion()) return;
        Vec3 cam = event.getCamera().getPosition();
        Vector3f look = event.getCamera().getLookVector();
        long now = mc.level.getGameTime();
        float partial = event.getPartialTick();

        List<Patch> patches = new ArrayList<>();
        DistortionSources.collect(mc, patches, now, partial, cam);
        patches.removeIf(p -> {
            if (faygolover.zoneartifacts.client.ClientAnomalyCache.hiddenAt(p.anchor)) return true;
            Vec3 to = p.anchor.subtract(cam);
            if (to.lengthSqr() > MAX_DISTANCE * MAX_DISTANCE) return true;
            // Behind the camera, all of it.
            return to.x * look.x() + to.y * look.y() + to.z * look.z() < -p.size - 0.5;
        });
        if (patches.isEmpty()) return;
        if (patches.size() > MAX_PATCHES) {
            patches.sort(Comparator.comparingDouble(p -> p.anchor.distanceToSqr(cam)));
            patches = patches.subList(0, MAX_PATCHES);
        }
        List<Patch> ready = new ArrayList<>();
        for (Patch p : patches) {
            if (p.prepare(cam)) ready.add(p);
        }
        if (ready.isEmpty()) return;

        Matrix4f pose = new Matrix4f(event.getPoseStack().last().pose());
        Matrix4f toScreen = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose);
        if (!grab(mc)) return;

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Patch p : ready) {
            draw(buffer, pose, toScreen, cam, p, 0.0f, 0.0f, 1.0f);
            if (p.blur > 1.0E-4) {
                // Blur (the air goes cloudy): the same picture a few times, slightly shifted.
                float b = (float) p.blur;
                draw(buffer, pose, toScreen, cam, p, b, 0.0f, 0.3f);
                draw(buffer, pose, toScreen, cam, p, -b, 0.0f, 0.3f);
                draw(buffer, pose, toScreen, cam, p, 0.0f, b, 0.3f);
                draw(buffer, pose, toScreen, cam, p, 0.0f, -b, 0.3f);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        List<Patch> split = ready.stream().filter(p -> p.chroma > 1.0E-5).toList();
        if (!split.isEmpty()) {
            // Red shifted one way, blue the other: only that channel is written each time.
            RenderSystem.colorMask(true, false, false, false);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Patch p : split) draw(buffer, pose, toScreen, cam, p, (float) p.chroma, (float) (p.chroma * 0.4), 1.0f);
            BufferUploader.drawWithShader(buffer.end());
            RenderSystem.colorMask(false, false, true, false);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Patch p : split) draw(buffer, pose, toScreen, cam, p, (float) -p.chroma, (float) (-p.chroma * 0.4), 1.0f);
            BufferUploader.drawWithShader(buffer.end());
            RenderSystem.colorMask(true, true, true, true);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void draw(BufferBuilder buffer, Matrix4f pose, Matrix4f toScreen, Vec3 cam, Patch patch,
                             float du, float dv, float alphaScale) {
        int rows = patch.rows();
        int cols = patch.cols();
        float[][] px = new float[rows + 1][cols + 1];
        float[][] py = new float[rows + 1][cols + 1];
        float[][] pz = new float[rows + 1][cols + 1];
        float[][] us = new float[rows + 1][cols + 1];
        float[][] vs = new float[rows + 1][cols + 1];
        float[][] as = new float[rows + 1][cols + 1];
        double[] out = new double[7];
        Vector4f tmp = new Vector4f();
        for (int i = 0; i <= rows; i++) {
            for (int j = 0; j <= cols; j++) {
                patch.point(i, j, out);
                px[i][j] = (float) (out[0] - cam.x);
                py[i][j] = (float) (out[1] - cam.y);
                pz[i][j] = (float) (out[2] - cam.z);
                tmp.set((float) (out[3] - cam.x), (float) (out[4] - cam.y), (float) (out[5] - cam.z), 1.0f);
                toScreen.transform(tmp);
                float w = Math.max(1.0E-4f, tmp.w);
                us[i][j] = tmp.x / w * 0.5f + 0.5f + du;
                vs[i][j] = tmp.y / w * 0.5f + 0.5f + dv;
                as[i][j] = (float) out[6] * alphaScale;
            }
        }
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (as[i][j] <= 0.004f && as[i + 1][j] <= 0.004f && as[i + 1][j + 1] <= 0.004f && as[i][j + 1] <= 0.004f) continue;
                vertex(buffer, pose, px, py, pz, us, vs, as, i, j);
                vertex(buffer, pose, px, py, pz, us, vs, as, i + 1, j);
                vertex(buffer, pose, px, py, pz, us, vs, as, i + 1, j + 1);
                vertex(buffer, pose, px, py, pz, us, vs, as, i, j + 1);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, float[][] px, float[][] py, float[][] pz,
                               float[][] us, float[][] vs, float[][] as, int i, int j) {
        buffer.vertex(pose, px[i][j], py[i][j], pz[i][j]).uv(us[i][j], vs[i][j])
                .color(1.0f, 1.0f, 1.0f, as[i][j]).endVertex();
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
