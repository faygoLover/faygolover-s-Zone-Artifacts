package faygolover.zoneartifacts.client.gravity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.Gravity;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.client.tesla.FireDraw;
import faygolover.zoneartifacts.config.ModClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The gravitational anomalies' look (no shaders — the "distortion" is faked):
 * <ul>
 *     <li><b>debris</b> — tiny spinning blocks, drawn with the real block models;</li>
 *     <li><b>Plesh</b> — a flattened, dusty patch on the ground; while pulling, rings of pressure
 *     closing in and a dark bubble thickening in the middle; the throw sends out a shock ring;</li>
 *     <li><b>Voronka</b> — refraction ({@link VoronkaLens}): barely visible at rest (fading in after
 *     the cooldown, nothing during it); while pulling, the distortion deepens, ripples run in, the
 *     air goes cloudy and a dark knot forms in the middle; the tear is a flash and a shock ring;</li>
 *     <li><b>Karusel</b> — twinkles of light inside (plus the whirl of dust and leaves); its blowout
 *     sends a flat wave out over the ground;</li>
 *     <li><b>Podushka</b> — only haze and hanging pebbles.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GravityRenderer {

    private static final int RELEASE_TICKS = 14;
    private static final int RING_POINTS = 40;

    private GravityRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || GravityClientHandler.states().isEmpty()) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            renderSolid(event, mc);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderGlow(event, mc);
        }
    }

    // ---- debris, ground patches, dark bubbles --------------------------------------------------

    private static void renderSolid(RenderLevelStageEvent event, Minecraft mc) {
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();

        // Debris: real block models, shrunk and spinning.
        for (GravityClientHandler.State state : GravityClientHandler.states()) {
            for (GravityClientHandler.Debris d : state.debris()) {
                Vec3 p = d.pos(partial);
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
                poseStack.pushPose();
                poseStack.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                poseStack.mulPose(new Quaternionf().rotationAxis(d.angle(partial), d.axis().x(), d.axis().y(), d.axis().z()));
                poseStack.scale(d.size(), d.size(), d.size());
                poseStack.translate(-0.5, -0.5, -0.5);
                blocks.renderSingleBlock(d.state(), poseStack, bufferSource, light, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
        bufferSource.endBatch();
    }

    /** The soft shapes go after the air distortion ({@code Distortion}, low priority), so the
     *  refraction doesn't bend them. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderShapes(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || GravityClientHandler.states().isEmpty()) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        // Soft dark shapes: alpha-blended, no depth writes.
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // Vanilla leaves the depth test off after the translucent layer: without this the
        // shapes would show through blocks.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (GravityClientHandler.State state : GravityClientHandler.states()) {
            Vec3 c = state.center();
            double half = state.entry().size() * 0.5;
            double reach = Gravity.reach(state.entry().size());
            float t = state.progress(now, partial);
            switch (state.kind()) {
                case PLESH -> {
                    Double ground = state.groundY();
                    if (ground != null) {
                        // The flattened, dusty patch.
                        flatBlob(buffer, matrix, new Vec3(c.x, ground + 0.012, c.z), half * 0.95, state.entry().pos().asLong(), 70, 58, 42, 60);
                    }
                    if (state.active()) {
                        // Pressure: a dark bubble thickening in the middle.
                        double r = Math.max(0.3, 0.9 - 0.5 * t);
                        disc(buffer, matrix, c, r, cam, 25, 22, 20, (int) (30 + 110 * t));
                    }
                }
                case VORONKA -> {
                    if (state.active()) {
                        // The air goes cloudy while pulling, and a dark knot forms at the very center.
                        double r = Math.max(half, reach * (1.0 - 0.45 * t));
                        disc(buffer, matrix, c, r * 0.8, cam, 150, 155, 165, (int) (55 * t * t));
                        disc(buffer, matrix, c, 0.25 + 0.35 * t, cam, 12, 12, 18, (int) (120 * t * t * t));
                    }
                }
                default -> {
                }
            }
        }

        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    // ---- rings, flashes, twinkles (additive) --------------------------------------------------------

    private static void renderGlow(RenderLevelStageEvent event, Minecraft mc) {
        float partial = event.getPartialTick();
        long now = mc.level.getGameTime();
        float time = (now % 72000L) + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(GlowRenderType.GLOW);

        for (GravityClientHandler.State state : GravityClientHandler.states()) {
            Vec3 c = state.center();
            double half = state.entry().size() * 0.5;
            double reach = Gravity.reach(state.entry().size());
            float t = state.progress(now, partial);
            float level = ModClientConfig.effective(state.entry().intensity()) / 3.0f;
            float release = (now - state.releaseTick() + partial) / RELEASE_TICKS;

            switch (state.kind()) {
                case PLESH -> {
                    if (state.active()) {
                        // Rings of pressure closing in, over and over.
                        for (int k = 0; k < 3; k++) {
                            float s = frac(time * 0.025f + k / 3.0f);
                            double r = reach * (1.0 - s) + 0.2;
                            int a = (int) (Math.min(1.0f, level) * 90 * 4 * s * (1 - s) * (0.5f + t));
                            ring(matrix, buffer, c, r, cam, 0.025f, FireDraw.argb(a, 215, 215, 205));
                        }
                    }
                    if (release >= 0 && release < 1) {
                        shockRing(matrix, buffer, c, reach, release, cam, 225, 220, 205);
                    }
                }
                case VORONKA -> {
                    // The zone's edge: a faint pale rim over the band of stronger refraction.
                    if (state.active()) {
                        double lensR = Math.max(half, reach * (1.0 - 0.45 * t));
                        double open = Math.min(1.0, t / 0.15);
                        double rim = Mth.lerp(open, half * 1.1, lensR) * VoronkaLens.RIM_AT;
                        ring(matrix, buffer, c, rim, cam, 0.012f, FireDraw.argb((int) (35 + 45 * t), 205, 220, 255));
                    } else {
                        float ready = state.readiness(now, partial);
                        if (ready > 0.0f) {
                            ring(matrix, buffer, c, half * 1.1 * VoronkaLens.RIM_AT, cam, 0.01f, FireDraw.argb((int) (22 * ready), 205, 220, 255));
                        }
                    }
                    // While pulling, faint rings run in.
                    if (state.active()) {
                        for (int k = 0; k < 2; k++) {
                            float s = frac(time * 0.03f + k * 0.5f);
                            double r = reach * (1.0 - s) + 0.15;
                            int a = (int) (Math.min(1.0f, level) * 70 * 4 * s * (1 - s) * t);
                            ring(matrix, buffer, c, r, cam, 0.012f, FireDraw.argb(a, 195, 205, 230));
                        }
                    }
                    if (release >= 0 && release < 1) {
                        float f = (1 - release) * (1 - release);
                        FireDraw.glow(matrix, buffer, c, 0.6 + 1.2 * release, cam, FireDraw.argb((int) (230 * f), 235, 240, 255), 20);
                        shockRing(matrix, buffer, c, reach, release, cam, 210, 225, 255);
                    }
                }
                case KARUSEL -> {
                    // The whirl: streaks of air spiralling in over the ground and rising up the axis.
                    float ready = state.readiness(now, partial);
                    float strength = state.active() ? Math.min(1.0f, 0.35f + t * 3.0f) : 0.18f * ready;
                    if (strength > 0.01f) {
                        double ground = state.groundY() != null ? state.groundY() : state.zone().minY;
                        whirl(matrix, buffer, new Vec3(c.x, ground, c.z), reach, time, state.active(), strength,
                                state.entry().pos().hashCode(), Math.min(1.0f, level), cam);
                    }
                    for (GravityClientHandler.Glint g : state.glints()) {
                        float life = (now - g.born() + partial) / g.life();
                        if (life < 0 || life > 1) continue;
                        int a = (int) (170 * Mth.sin((float) Math.PI * life));
                        FireDraw.glow(matrix, buffer, g.pos(), g.size(), cam, FireDraw.argb(a, 230, 240, 255), 8);
                    }
                    if (release >= 0 && release < 1) {
                        // The wave of compressed air spreads flat, low over the ground.
                        double ground = state.groundY() != null ? state.groundY() : state.zone().minY;
                        for (int k = 0; k < 3; k++) {
                            double y = ground + 0.15 + k * reach * 0.3;
                            float lag = Math.max(0.0f, release - k * 0.06f);
                            flatShockRing(matrix, buffer, new Vec3(c.x, y, c.z), reach, lag, cam, 1.0f - k * 0.3f);
                        }
                    }
                }
                default -> {
                }
            }
        }

        bufferSource.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** An expanding ring fading out, for throws, tears and hits. */
    private static void shockRing(Matrix4f matrix, VertexConsumer buffer, Vec3 c, double reach, float life, Vec3 cam, int r, int g, int b) {
        float e = 1.0f - (1.0f - life) * (1.0f - life);
        double radius = 0.3 + reach * 1.6 * e;
        int alpha = (int) (200 * (1.0f - life));
        ring(matrix, buffer, c, radius, cam, 0.04f + 0.08f * (1 - life), FireDraw.argb(alpha, r, g, b));
    }

    /**
     * Karusel's whirl: a few streaks of air, each a spiral from the rim over the ground in to the
     * axis and up it, turning — quick and visible while it spins, a faint slow stir at rest.
     */
    private static void whirl(Matrix4f matrix, VertexConsumer buffer, Vec3 base, double reach, float time, boolean active,
                              float strength, int seed, float level, Vec3 cam) {
        int arms = active ? 5 : 3;
        int points = 36;
        float turn = time * (active ? 0.16f : 0.03f);
        for (int k = 0; k < arms; k++) {
            float phase = (float) (Math.PI * 2.0 * k / arms) + (seed & 0xFF) * 0.1f;
            // Each streak runs in along its own path: it slides round so the air visibly flows inward.
            float flow = frac(time * (active ? 0.02f : 0.006f) + k / (float) arms);
            Vec3[] pts = new Vec3[points + 1];
            float[] widths = new float[points + 1];
            int[] colors = new int[points + 1];
            for (int i = 0; i <= points; i++) {
                double s = i / (double) points;
                double r = reach * 0.95 * Math.pow(1.0 - s, 1.3) + 0.12;
                double a = phase + turn + s * Math.PI * 2.6;
                double y = base.y + 0.06 + Math.pow(s, 1.8) * reach * 0.9;
                pts[i] = new Vec3(base.x + Math.cos(a) * r, y, base.z + Math.sin(a) * r);
                // A brighter pulse travelling in along the streak; the ends fade.
                double pulse = Math.exp(-Math.pow((s - flow) / 0.18, 2.0));
                double ends = Math.min(1.0, s / 0.12) * Math.min(1.0, (1.0 - s) / 0.25);
                int alpha = (int) (strength * level * (active ? 75 : 45) * ends * (0.35 + 0.65 * pulse));
                widths[i] = (float) ((active ? 0.03 : 0.02) + 0.03 * s);
                colors[i] = FireDraw.argb(alpha, 225, 228, 232);
            }
            FireDraw.ribbon(matrix, buffer, pts, widths, colors, cam);
        }
    }

    /** Karusel's wave: a ring lying flat, rushing out and fading. */
    private static void flatShockRing(Matrix4f matrix, VertexConsumer buffer, Vec3 c, double reach, float life, Vec3 cam, float strength) {
        if (life <= 0.0f || life >= 1.0f) return;
        float e = 1.0f - (1.0f - life) * (1.0f - life);
        double radius = 0.3 + reach * 1.5 * e;
        int alpha = (int) (170 * strength * (1.0f - life));
        if (alpha <= 2) return;
        Vec3[] points = new Vec3[RING_POINTS + 1];
        float[] widths = new float[RING_POINTS + 1];
        int[] colors = new int[RING_POINTS + 1];
        for (int i = 0; i <= RING_POINTS; i++) {
            double a = Math.PI * 2.0 * i / RING_POINTS;
            points[i] = new Vec3(c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius);
            widths[i] = 0.05f + 0.1f * (1 - life);
            colors[i] = FireDraw.argb(alpha, 225, 225, 215);
        }
        FireDraw.ribbon(matrix, buffer, points, widths, colors, cam);
    }

    /** A camera-facing circle drawn as a ribbon. */
    private static void ring(Matrix4f matrix, VertexConsumer buffer, Vec3 c, double radius, Vec3 cam, float halfWidth, int color) {
        if (((color >>> 24) & 0xFF) <= 2 || radius <= 0.01) return;
        Vec3 toCam = cam.subtract(c);
        if (toCam.lengthSqr() < 1.0E-6) return;
        toCam = toCam.normalize();
        Vec3 up = Math.abs(toCam.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = toCam.cross(up).normalize();
        Vec3 upOnPlane = right.cross(toCam).normalize();
        Vec3[] points = new Vec3[RING_POINTS + 1];
        float[] widths = new float[RING_POINTS + 1];
        int[] colors = new int[RING_POINTS + 1];
        for (int i = 0; i <= RING_POINTS; i++) {
            double a = Math.PI * 2.0 * i / RING_POINTS;
            points[i] = c.add(right.scale(Math.cos(a) * radius)).add(upOnPlane.scale(Math.sin(a) * radius));
            widths[i] = halfWidth;
            colors[i] = color;
        }
        FireDraw.ribbon(matrix, buffer, points, widths, colors, cam);
    }

    /** A camera-facing soft disc (dark: plain alpha blending), a fan of quads with a repeated corner. */
    private static void disc(BufferBuilder buffer, Matrix4f m, Vec3 c, double radius, Vec3 cam, int r, int g, int b, int alpha) {
        Vec3 toCam = cam.subtract(c);
        if (toCam.lengthSqr() < 1.0E-6 || alpha <= 2) return;
        toCam = toCam.normalize();
        Vec3 up = Math.abs(toCam.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = toCam.cross(up).normalize();
        Vec3 upOnPlane = right.cross(toCam).normalize();
        int slices = 24;
        for (int i = 0; i < slices; i++) {
            double a0 = Math.PI * 2.0 * i / slices;
            double a1 = Math.PI * 2.0 * (i + 1) / slices;
            Vec3 p0 = c.add(right.scale(Math.cos(a0) * radius)).add(upOnPlane.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(right.scale(Math.cos(a1) * radius)).add(upOnPlane.scale(Math.sin(a1) * radius));
            buffer.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(r, g, b, alpha).endVertex();
            buffer.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, 0).endVertex();
            buffer.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, 0).endVertex();
            buffer.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, 0).endVertex();
        }
    }

    /** An irregular flat patch lying on the ground, seeded so it keeps its shape. */
    private static void flatBlob(BufferBuilder buffer, Matrix4f m, Vec3 c, double radius, long seed, int r, int g, int b, int alpha) {
        java.util.Random shape = new java.util.Random(seed);
        int points = 16;
        double[] radii = new double[points];
        for (int i = 0; i < points; i++) radii[i] = radius * (0.75 + shape.nextDouble() * 0.3);
        for (int i = 0; i < points; i++) {
            double a0 = Math.PI * 2.0 * i / points;
            double a1 = Math.PI * 2.0 * (i + 1) / points;
            double r0 = radii[i];
            double r1 = radii[(i + 1) % points];
            buffer.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(r, g, b, alpha).endVertex();
            buffer.vertex(m, (float) (c.x + Math.cos(a0) * r0), (float) c.y, (float) (c.z + Math.sin(a0) * r0)).color(r, g, b, alpha / 3).endVertex();
            buffer.vertex(m, (float) (c.x + Math.cos(a1) * r1), (float) c.y, (float) (c.z + Math.sin(a1) * r1)).color(r, g, b, alpha / 3).endVertex();
            buffer.vertex(m, (float) (c.x + Math.cos(a1) * r1), (float) c.y, (float) (c.z + Math.sin(a1) * r1)).color(r, g, b, alpha / 3).endVertex();
        }
    }
}
