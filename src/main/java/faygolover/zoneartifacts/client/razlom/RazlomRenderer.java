package faygolover.zoneartifacts.client.razlom;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.client.tesla.FireDraw;
import faygolover.zoneartifacts.config.ModClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws every Razlom in range (the Cold Razlom in soul-fire colours, see {@link Palette}):
 * <ul>
 *     <li><b>cracks</b> — dark jagged bands lying on the ground (plain alpha blending, so they
 *     really darken the blocks), each with a thin glowing seam of fire along its middle (additive),
 *     pulsing slowly; brighter while the jet burns, dimmer while it rests;</li>
 *     <li><b>the flame</b> hovering over the crossing point: a flickering glow with a couple of
 *     small tongues licking upwards;</li>
 *     <li><b>the jet</b> — twisting streams of fire from the flame to the target, widening towards
 *     it, cut short by any block in between.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RazlomRenderer {

    /** Overall size of the hovering flame. */
    private static final float FLAME_SCALE = 1.35f;
    private static final float DARK_HALF_WIDTH = 0.075f;
    private static final float SEAM_HALF_WIDTH = 0.022f;
    private static final double DARK_LIFT = 0.006;
    private static final double SEAM_LIFT = 0.009;

    /** Colours of one kind of Razlom: fire, or the Cold Razlom's soul fire. {@code dark} is the
     *  crack's RGB (its alpha varies along the crack). */
    private record Palette(int seamHot, int seamDim, int flameOuter, int flameInner,
                           int jetBase, int jetMid, int jetTip, int dark) {
        int dark(int alpha) {
            return (Mth.clamp(alpha, 0, 255) << 24) | (dark & 0xFFFFFF);
        }
    }

    private static final Palette FIRE = new Palette(
            FireDraw.argb(230, 255, 170, 50), FireDraw.argb(170, 230, 70, 15),
            FireDraw.argb(130, 255, 100, 20), FireDraw.argb(220, 255, 215, 110),
            FireDraw.argb(235, 255, 235, 170), FireDraw.argb(200, 255, 140, 30), FireDraw.argb(110, 200, 40, 10),
            0x140C08);

    private static final Palette SOUL = new Palette(
            FireDraw.argb(230, 120, 245, 255), FireDraw.argb(170, 30, 110, 220),
            FireDraw.argb(130, 40, 150, 235), FireDraw.argb(220, 190, 250, 255),
            FireDraw.argb(235, 225, 255, 255), FireDraw.argb(200, 60, 200, 245), FireDraw.argb(110, 25, 60, 180),
            0x080E18);

    private static Palette palette(RazlomClientHandler.State state) {
        return state.cold() ? SOUL : FIRE;
    }

    private RazlomRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RazlomClientHandler.states().isEmpty()) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            renderDark(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderFire(event, mc);
        }
    }

    // ---- dark bands (immediate mode, alpha blended) ------------------------------------

    private static void renderDark(RenderLevelStageEvent event) {
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (RazlomClientHandler.State state : RazlomClientHandler.states()) {
            Palette pal = palette(state);
            float widthScale = (float) Mth.clamp(Math.sqrt(state.entry().size()), 1.0, 2.0);
            for (RazlomClientHandler.Crack crack : state.cracks()) {
                for (int i = 0; i < crack.xs.length - 1; i++) {
                    if (!flatSegment(crack, i)) continue;
                    float wa = DARK_HALF_WIDTH * widthScale * (0.4f + 0.6f * crack.widths[i]);
                    float wb = DARK_HALF_WIDTH * widthScale * (0.4f + 0.6f * crack.widths[i + 1]);
                    int aa = (int) (150 + 90 * crack.widths[i]);
                    int ab = (int) (150 + 90 * crack.widths[i + 1]);
                    flatQuad(buffer, matrix, crack, i, crack.ys[i] + DARK_LIFT, wa, wb,
                            pal.dark(aa), pal.dark(ab));
                }
            }
        }

        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    // ---- seams, flame and jet (additive) ---------------------------------------------------

    private static void renderFire(RenderLevelStageEvent event, Minecraft mc) {
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        float time = (now % 72000L) + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        for (RazlomClientHandler.State state : RazlomClientHandler.states()) {
            Palette pal = palette(state);
            boolean jetting = state.jetActive(now);
            boolean resting = state.entry().onCooldown();
            float level = jetting ? 1.25f : resting ? 0.55f : 1.0f;
            float widthScale = (float) Mth.clamp(Math.sqrt(state.entry().size()), 1.0, 2.0);
            float seed = (state.entry().pos().hashCode() & 0xFFFF) / 6553.6f;

            // Seams: flat glowing lines on the ground, pulsing.
            for (RazlomClientHandler.Crack crack : state.cracks()) {
                for (int i = 0; i < crack.xs.length - 1; i++) {
                    // The seam stops a segment short of the dark crack's free ends.
                    if (!crack.seamCovers(i) || !flatSegment(crack, i)) continue;
                    float pa = 0.75f + 0.25f * Mth.sin(time * 0.08f + i * 0.5f + seed);
                    float pb = 0.75f + 0.25f * Mth.sin(time * 0.08f + (i + 1) * 0.5f + seed);
                    int ca = FireDraw.fade(FireDraw.mix(pal.seamDim(), pal.seamHot(), crack.widths[i] * pa), level * pa);
                    int cb = FireDraw.fade(FireDraw.mix(pal.seamDim(), pal.seamHot(), crack.widths[i + 1] * pb), level * pb);
                    float wa = SEAM_HALF_WIDTH * widthScale * (0.35f + 0.65f * crack.widths[i]);
                    float wb = SEAM_HALF_WIDTH * widthScale * (0.35f + 0.65f * crack.widths[i + 1]);
                    flatQuadUp(buffer, matrix, crack, i, crack.ys[i] + SEAM_LIFT, wa, wb, ca, cb);
                }
            }

            Vec3 f = state.flame();
            // The flame is out while resting (it fades out and flares up again).
            float flameSize = FLAME_SCALE * (jetting ? 1.35f : 1.0f) * state.flameLevel(partial);

            // Jet streams: shooting out along the arc, stopped by the first block in the way.
            RazlomClientHandler.Jet jet = state.jet();
            if (jetting && jet != null) {
                drawJet(mc, matrix, buffer, pal, f, jet.aim(partial), state.jetExtend(now, partial), time,
                        ModClientConfig.effective(state.entry().intensity()), cam);
            }
            if (flameSize < 0.02f) continue;

            // Flame tongues, then its glow last (the render type writes depth).
            for (int k = 0; k < 2; k++) {
                float sway = Mth.sin(time * 0.3f + k * 2.1f + seed) * 0.35f;
                Vec3 bend = new Vec3(sway, 0.0, Mth.cos(time * 0.27f + k * 1.7f + seed) * 0.35f);
                double length = (0.22 + 0.06 * Mth.sin(time * 0.45f + k)) * flameSize;
                Vec3[] points = FireDraw.tongue(f.add(0, -0.05, 0), new Vec3(0, 1, 0), bend, length, 5);
                float[] widths = new float[points.length];
                int[] colors = new int[points.length];
                for (int i = 0; i < points.length; i++) {
                    float s = i / (float) (points.length - 1);
                    widths[i] = 0.06f * flameSize * (1.0f - s);
                    colors[i] = FireDraw.fade(FireDraw.mix(pal.flameInner(), pal.flameOuter(), s), 1.0f - 0.5f * s);
                }
                FireDraw.ribbon(matrix, buffer, points, widths, colors, cam);
            }
            float flicker = 1.0f + 0.12f * Mth.sin(time * 0.9f + seed) + 0.06f * Mth.sin(time * 2.3f);
            FireDraw.glow(matrix, buffer, f, 0.34 * flameSize * flicker, cam, pal.flameOuter(), 16);
            FireDraw.glow(matrix, buffer, f, 0.14 * flameSize * flicker, cam, pal.flameInner(), 12);
        }

        bufferSource.endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    /**
     * Twisting streams along the jet's arc ({@link Razlom#jetPoint}) from the flame towards
     * {@code to}, drawn only as far as it has shot out ({@code extend}, 0..1) and cut at the first
     * block in the way; narrow at the flame, wide at the far end.
     */
    private static void drawJet(Minecraft mc, Matrix4f matrix, VertexConsumer buffer, Palette pal, Vec3 from, Vec3 to, float extend,
                                float time, int intensity, Vec3 cam) {
        if (extend <= 0.01f || to.distanceToSqr(from) < 0.0025) return;
        Razlom.Arc arc = Razlom.arc(mc.level, from, to, extend);
        List<Vec3> path = arc.points();
        // Parameter along the whole arc for each point (the last may be cut short by a block).
        List<Double> params = new ArrayList<>(path.size());
        for (int i = 0; i < path.size(); i++) {
            params.add(Math.min(extend, extend * i / (double) Razlom.JET_SEGMENTS));
        }
        int n = path.size();
        if (n < 2) return;

        int streams = Mth.clamp(2 + intensity / 3, 2, 5);
        for (int s = 0; s < streams; s++) {
            float phase = s * 2.4f;
            Vec3[] points = new Vec3[n];
            float[] widths = new float[n];
            int[] colors = new int[n];
            for (int i = 0; i < n; i++) {
                double t = params.get(i);
                Vec3 tangent = path.get(Math.min(n - 1, i + 1)).subtract(path.get(Math.max(0, i - 1)));
                Vec3 d = tangent.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : tangent.normalize();
                Vec3 helper = Math.abs(d.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                Vec3 p1 = d.cross(helper).normalize();
                Vec3 p2 = d.cross(p1).normalize();
                double swirl = 0.12 * t * Math.sin(time * 0.9 + t * 9.0 + phase);
                double swirl2 = 0.12 * t * Math.cos(time * 0.8 + t * 7.0 + phase * 1.3);
                points[i] = path.get(i).add(p1.scale(swirl)).add(p2.scale(swirl2));
                widths[i] = (float) (0.035 + 0.16 * t) * (s == 0 ? 1.0f : 0.7f);
                int c = t < 0.35 ? FireDraw.mix(pal.jetBase(), pal.jetMid(), (float) (t / 0.35)) : FireDraw.mix(pal.jetMid(), pal.jetTip(), (float) ((t - 0.35) / 0.65));
                colors[i] = s == 0 ? c : FireDraw.fade(c, 0.7f);
            }
            FireDraw.ribbon(matrix, buffer, points, widths, colors, cam);
        }
    }

    /** Both ends on ground at the same height (a step breaks the line). */
    private static boolean flatSegment(RazlomClientHandler.Crack crack, int i) {
        Double a = crack.ys[i];
        Double b = crack.ys[i + 1];
        return a != null && b != null && Math.abs(a - b) < 0.01;
    }

    /** Horizontal quad along a crack segment, for the immediate-mode dark pass (culling off). */
    private static void flatQuad(BufferBuilder buffer, Matrix4f matrix, RazlomClientHandler.Crack crack, int i, double y,
                                 float wa, float wb, int ca, int cb) {
        double ax = crack.xs[i], az = crack.zs[i], bx = crack.xs[i + 1], bz = crack.zs[i + 1];
        double dx = bx - ax, dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5) return;
        double sx = -dz / len, sz = dx / len;
        put(buffer, matrix, ax - sx * wa, y, az - sz * wa, ca);
        put(buffer, matrix, bx - sx * wb, y, bz - sz * wb, cb);
        put(buffer, matrix, bx + sx * wb, y, bz + sz * wb, cb);
        put(buffer, matrix, ax + sx * wa, y, az + sz * wa, ca);
    }

    /** Horizontal quad wound counter-clockwise seen from above (the lightning type culls back faces). */
    private static void flatQuadUp(VertexConsumer buffer, Matrix4f matrix, RazlomClientHandler.Crack crack, int i, double y,
                                   float wa, float wb, int ca, int cb) {
        double ax = crack.xs[i], az = crack.zs[i], bx = crack.xs[i + 1], bz = crack.zs[i + 1];
        double dx = bx - ax, dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5) return;
        dx /= len;
        dz /= len;
        // e = up x d = (dz, 0, -dx): (a - e, b - e, b + e, a + e) is CCW seen from +Y.
        double ex = dz, ez = -dx;
        put(buffer, matrix, ax - ex * wa, y, az - ez * wa, ca);
        put(buffer, matrix, bx - ex * wb, y, bz - ez * wb, cb);
        put(buffer, matrix, bx + ex * wb, y, bz + ez * wb, cb);
        put(buffer, matrix, ax + ex * wa, y, az + ez * wa, ca);
    }

    private static void put(VertexConsumer buffer, Matrix4f matrix, double x, double y, double z, int color) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF).endVertex();
    }
}
