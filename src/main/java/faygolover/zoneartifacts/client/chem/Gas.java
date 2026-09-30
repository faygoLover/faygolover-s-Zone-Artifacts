package faygolover.zoneartifacts.client.chem;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gas: soft, ragged, non-glowing puffs (plain alpha blending, lit by the world's light, so they
 * look like heavy vapour rather than light). Loose puffs drift, settle towards a height and fade
 * ({@link #add}); anything can also hand in puffs for just this frame ({@link #addFrameSource}), e.g.
 * the Chemical Comet's churning body. Drawn back to front after the clouds.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Gas {

    private static final int MAX_PUFFS = 1500;
    private static final int SEGMENTS = 14;

    /** One puff. Colours are RGB; {@code alpha} is its peak opacity. */
    public static final class Puff {
        Vec3 pos;
        Vec3 prev;
        Vec3 vel;
        double size0;
        double size1;
        long born;
        int life;
        float alpha;
        int inner;
        int outer;
        /** Settles towards this height (NaN: free). */
        double settleY = Double.NaN;
        double drag = 0.94;
        float seed;
        /** Never darker than this (a faint glow of its own). */
        float minLight;
        /** Fades in daylight (hard to see by day). */
        boolean dayFade;

        public Puff(Vec3 pos, Vec3 vel, double size0, double size1, long born, int life, float alpha, int inner, int outer, float seed) {
            this.pos = pos;
            this.prev = pos;
            this.vel = vel;
            this.size0 = size0;
            this.size1 = size1;
            this.born = born;
            this.life = life;
            this.alpha = alpha;
            this.inner = inner;
            this.outer = outer;
            this.seed = seed;
        }

        public Puff settle(double y) {
            this.settleY = y;
            return this;
        }

        public Puff drag(double drag) {
            this.drag = drag;
            return this;
        }

        public Puff glow(float minLight) {
            this.minLight = minLight;
            return this;
        }

        public Puff dayFade() {
            this.dayFade = true;
            return this;
        }
    }

    /** A puff drawn for one frame only: world position, radius, peak alpha, colours. {@code liquid}:
     *  not vapour but a quivering drop of liquid — smooth-edged, glossy, faintly glowing. */
    public record FramePuff(Vec3 pos, double radius, float alpha, int inner, int outer, float seed, boolean liquid,
                            float minLight, boolean dayFade) {
        public FramePuff(Vec3 pos, double radius, float alpha, int inner, int outer, float seed) {
            this(pos, radius, alpha, inner, outer, seed, false, 0.0f, false);
        }

        public FramePuff(Vec3 pos, double radius, float alpha, int inner, int outer, float seed, boolean liquid) {
            this(pos, radius, alpha, inner, outer, seed, liquid, 0.0f, false);
        }
    }

    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final List<Consumer<List<FramePuff>>> FRAME_SOURCES = new ArrayList<>();
    private static ClientLevel lastLevel;

    private Gas() {
    }

    public static void add(Puff puff) {
        if (PUFFS.size() >= MAX_PUFFS) PUFFS.remove(0);
        PUFFS.add(puff);
    }

    /** Registers something that hands in puffs every frame (called once, at class init of the source). */
    public static void addFrameSource(Consumer<List<FramePuff>> source) {
        FRAME_SOURCES.add(source);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            PUFFS.clear();
            lastLevel = mc.level;
        }
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        for (Iterator<Puff> it = PUFFS.iterator(); it.hasNext(); ) {
            Puff p = it.next();
            if (now - p.born > p.life) {
                it.remove();
                continue;
            }
            p.prev = p.pos;
            Vec3 v = p.vel.scale(p.drag);
            if (!Double.isNaN(p.settleY)) v = v.add(0.0, (p.settleY - p.pos.y) * 0.03 - v.y * 0.1, 0.0);
            p.vel = v;
            p.pos = p.pos.add(v);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float partial = event.getPartialTick();
        long now = level.getGameTime();
        float time = (now % 72000L) + partial;

        List<FramePuff> frame = new ArrayList<>();
        for (Consumer<List<FramePuff>> source : FRAME_SOURCES) source.accept(frame);
        for (Puff p : PUFFS) {
            float age = (now - p.born + partial) / (float) p.life;
            if (age < 0.0f || age > 1.0f) continue;
            float in = Math.min(1.0f, (now - p.born + partial) / 8.0f);
            float out = age < 0.65f ? 1.0f : (1.0f - age) / 0.35f;
            double grow = 1.0 - (1.0 - age) * (1.0 - age);
            frame.add(new FramePuff(p.prev.lerp(p.pos, partial), Mth.lerp(grow, p.size0, p.size1), p.alpha * in * out,
                    p.inner, p.outer, p.seed, false, p.minLight, p.dayFade));
        }
        if (frame.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        frame.removeIf(f -> faygolover.zoneartifacts.client.ClientAnomalyCache.hiddenAt(f.pos()));
        frame.sort(Comparator.comparingDouble((FramePuff f) -> f.pos().distanceToSqr(cam)).reversed());

        PoseStack poseStack = lateStagePose(event);
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float skyDarken = level.getSkyDarken(partial);
        for (FramePuff f : frame) {
            if (f.alpha() <= 0.004f || f.radius() < 0.01) continue;
            if (f.liquid()) {
                drop(buffer, m, f, cam, Math.max(0.6f, light(level, f.pos(), skyDarken)), time);
                continue;
            }
            FramePuff g = f;
            if (f.dayFade()) {
                // Hard to see by day: fades the more daylight falls on it.
                int packed = LevelRenderer.getLightColor(level, BlockPos.containing(f.pos()));
                float day = LightTexture.sky(packed) / 15.0f * skyDarken;
                g = new FramePuff(f.pos(), f.radius(), f.alpha() * (1.0f - 0.7f * day), f.inner(), f.outer(), f.seed(), false, f.minLight(), true);
                if (g.alpha() <= 0.004f) continue;
            }
            puff(buffer, m, g, cam, Math.max(f.minLight(), light(level, f.pos(), skyDarken)), time);
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * The pose for drawing in world space at the late stages (after the clouds): vanilla has already
     * put the camera's rotation into the model-view matrix there, and the event's pose has it too —
     * undo the first so it isn't applied twice.
     */
    public static PoseStack lateStagePose(RenderLevelStageEvent event) {
        Matrix4f base = new Matrix4f(RenderSystem.getModelViewMatrix()).invert().mul(event.getPoseStack().last().pose());
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(base);
        return poseStack;
    }

    /** 0.15..1: the world's light where the puff is (gas doesn't glow). */
    private static float light(ClientLevel level, Vec3 p, float skyDarken) {
        int packed = LevelRenderer.getLightColor(level, BlockPos.containing(p));
        float block = LightTexture.block(packed) / 15.0f;
        float sky = LightTexture.sky(packed) / 15.0f * skyDarken;
        return 0.15f + 0.85f * Math.max(block, sky);
    }

    /** A camera-facing ragged disc: dense middle, a ring at 55 %, a soft torn rim. */
    private static void puff(BufferBuilder buffer, Matrix4f m, FramePuff f, Vec3 cam, float light, float time) {
        Vec3 c = f.pos();
        Vec3 toCam = cam.subtract(c);
        if (toCam.lengthSqr() < 1.0E-6) return;
        toCam = toCam.normalize();
        Vec3 up = Math.abs(toCam.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = toCam.cross(up).normalize();
        Vec3 upOnPlane = right.cross(toCam).normalize();
        int a0 = (int) (255 * Mth.clamp(f.alpha(), 0.0f, 1.0f));
        int a1 = (int) (a0 * 0.8f);
        int in = shade(f.inner(), light);
        int mid = shade(mix(f.inner(), f.outer(), 0.5f), light);
        int out = shade(f.outer(), light);
        double r = f.radius();
        for (int i = 0; i < SEGMENTS; i++) {
            double t0 = Math.PI * 2.0 * i / SEGMENTS;
            double t1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
            double w0 = rim(t0, f.seed(), time);
            double w1 = rim(t1, f.seed(), time);
            Vec3 d0 = right.scale(Math.cos(t0)).add(upOnPlane.scale(Math.sin(t0)));
            Vec3 d1 = right.scale(Math.cos(t1)).add(upOnPlane.scale(Math.sin(t1)));
            Vec3 m0 = c.add(d0.scale(r * 0.55 * w0));
            Vec3 m1 = c.add(d1.scale(r * 0.55 * w1));
            Vec3 o0 = c.add(d0.scale(r * w0));
            Vec3 o1 = c.add(d1.scale(r * w1));
            put(buffer, m, c, in, a0);
            put(buffer, m, m0, mid, a1);
            put(buffer, m, m1, mid, a1);
            put(buffer, m, m1, mid, a1);
            put(buffer, m, m0, mid, a1);
            put(buffer, m, o0, out, 0);
            put(buffer, m, o1, out, 0);
            put(buffer, m, m1, mid, a1);
        }
    }

    /**
     * A drop of liquid facing the camera: smooth, gently quivering rim, bright in the middle and
     * darker to the edge (a rounded, glossy look), a pale highlight up and to one side.
     */
    private static void drop(BufferBuilder buffer, Matrix4f m, FramePuff f, Vec3 cam, float light, float time) {
        Vec3 c = f.pos();
        Vec3 toCam = cam.subtract(c);
        if (toCam.lengthSqr() < 1.0E-6) return;
        toCam = toCam.normalize();
        Vec3 up = Math.abs(toCam.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = toCam.cross(up).normalize();
        Vec3 upOnPlane = right.cross(toCam).normalize();
        int a = (int) (255 * Mth.clamp(f.alpha(), 0.0f, 1.0f));
        int core = shade(f.inner(), light);
        int rim = shade(f.outer(), light * 0.8f);
        double r = f.radius();
        Vec3 front = c.add(toCam.scale(r * 0.02));
        for (int i = 0; i < SEGMENTS; i++) {
            double t0 = Math.PI * 2.0 * i / SEGMENTS;
            double t1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
            double w0 = 1.0 + 0.05 * Math.sin(t0 * 3.0 + time * 0.2 + f.seed()) + 0.03 * Math.sin(t0 * 5.0 - time * 0.27);
            double w1 = 1.0 + 0.05 * Math.sin(t1 * 3.0 + time * 0.2 + f.seed()) + 0.03 * Math.sin(t1 * 5.0 - time * 0.27);
            Vec3 d0 = right.scale(Math.cos(t0)).add(upOnPlane.scale(Math.sin(t0)));
            Vec3 d1 = right.scale(Math.cos(t1)).add(upOnPlane.scale(Math.sin(t1)));
            Vec3 m0 = front.add(d0.scale(r * 0.6 * w0));
            Vec3 m1 = front.add(d1.scale(r * 0.6 * w1));
            Vec3 o0 = front.add(d0.scale(r * w0));
            Vec3 o1 = front.add(d1.scale(r * w1));
            Vec3 e0 = front.add(d0.scale(r * w0 * 1.1));
            Vec3 e1 = front.add(d1.scale(r * w1 * 1.1));
            put(buffer, m, front, core, a);
            put(buffer, m, m0, core, a);
            put(buffer, m, m1, core, a);
            put(buffer, m, m1, core, a);
            put(buffer, m, m0, core, a);
            put(buffer, m, o0, rim, a);
            put(buffer, m, o1, rim, a);
            put(buffer, m, m1, core, a);
            put(buffer, m, o0, rim, a);
            put(buffer, m, e0, rim, 0);
            put(buffer, m, e1, rim, 0);
            put(buffer, m, o1, rim, a);
        }
        // Gloss.
        Vec3 hc = front.add(toCam.scale(r * 0.01)).add(right.scale(-r * 0.32)).add(upOnPlane.scale(r * 0.34));
        double hr = r * 0.2;
        int hi = shade(0xF2FFD8, Math.min(1.0f, light + 0.2f));
        for (int i = 0; i < 10; i++) {
            double t0 = Math.PI * 2.0 * i / 10;
            double t1 = Math.PI * 2.0 * (i + 1) / 10;
            Vec3 p0 = hc.add(right.scale(Math.cos(t0) * hr * 1.3)).add(upOnPlane.scale(Math.sin(t0) * hr));
            Vec3 p1 = hc.add(right.scale(Math.cos(t1) * hr * 1.3)).add(upOnPlane.scale(Math.sin(t1) * hr));
            put(buffer, m, hc, hi, (int) (a * 0.7f));
            put(buffer, m, p0, hi, 0);
            put(buffer, m, p1, hi, 0);
            put(buffer, m, p1, hi, 0);
        }
    }

    /** Torn, slowly changing edge. */
    private static double rim(double angle, float seed, float time) {
        return 0.8 + 0.12 * Math.sin(angle * 3.0 + seed + time * 0.03) + 0.08 * Math.sin(angle * 5.0 + seed * 1.7 - time * 0.05);
    }

    private static void put(BufferBuilder buffer, Matrix4f m, Vec3 p, int rgb, int alpha) {
        buffer.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).endVertex();
    }

    private static int shade(int rgb, float light) {
        int r = (int) (((rgb >> 16) & 0xFF) * light);
        int g = (int) (((rgb >> 8) & 0xFF) * light);
        int b = (int) ((rgb & 0xFF) * light);
        return (r << 16) | (g << 8) | b;
    }

    public static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
