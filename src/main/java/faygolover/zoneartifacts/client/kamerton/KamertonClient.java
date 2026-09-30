package faygolover.zoneartifacts.client.kamerton;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.client.ZoneLoopSound;
import faygolover.zoneartifacts.client.distortion.Distortion;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kamerton's look: a lumpy cloud of thin glass needles, each pointing its own way — pale,
 * translucent, glinting as the eye moves (brightest seen side-on), twinkling here and there. They
 * lean aside round whoever is among them. A quiet high ringing, louder while something moves in it,
 * and a faint shimmer of the air.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KamertonClient {

    private static final double NEAR = 64.0;
    private static final int MAX_NEEDLES = 1500;
    private static final int NEEDLE = 8;

    private static final class Ball {
        SyncAnomaliesPacket.Entry entry;
        float[] needles = new float[0]; // NEEDLE floats each: middle x,y,z (from the centre), direction x,y,z, length, seed
        float activity;
        float prevActivity;
        List<AABB> bodies = new ArrayList<>();
        @Nullable
        ZoneLoopSound ring;
    }

    private static final Map<BlockPos, Ball> BALLS = new HashMap<>();

    private KamertonClient() {
    }

    private static void build(Ball b) {
        double r = b.entry.size() / 2.0;
        int eff = ModClientConfig.effective(b.entry.intensity());
        RandomSource rand = RandomSource.create(b.entry.pos().asLong() * 7919L);
        double scale = Math.min(1.0, r / 2.5) * 0.7 + 0.3;
        // A lumpy cloud rather than a ball: how far it reaches in each direction wanders (a few
        // slow waves over the directions), always within the zone's own ball.
        double[][] lumps = new double[4][4];
        for (double[] l : lumps) {
            Vec3 d = new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian()).normalize();
            l[0] = d.x;
            l[1] = d.y;
            l[2] = d.z;
            l[3] = 1.5 + rand.nextDouble() * 2.5;
        }
        int n = Mth.clamp((int) (4.0 / 3.0 * Math.PI * r * r * r * 6.5 * eff / 3.0), 20, MAX_NEEDLES);
        float[] arr = new float[n * NEEDLE];
        for (int i = 0; i < n; i++) {
            Vec3 at = new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian()).normalize();
            double reach = 0.0;
            for (double[] l : lumps) reach += Math.sin((at.x * l[0] + at.y * l[1] + at.z * l[2]) * l[3] + l[3]);
            reach = r * (0.62 + 0.38 * (0.5 + 0.5 * Math.tanh(reach * 0.6)));
            double rho = reach * Math.cbrt(rand.nextDouble());
            // Each one pointing its own way.
            Vec3 dir = new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian()).normalize();
            int k = i * NEEDLE;
            arr[k] = (float) (at.x * rho);
            arr[k + 1] = (float) (at.y * rho);
            arr[k + 2] = (float) (at.z * rho);
            arr[k + 3] = (float) dir.x;
            arr[k + 4] = (float) dir.y;
            arr[k + 5] = (float) dir.z;
            arr[k + 6] = (float) ((0.22 + rand.nextDouble() * 0.3) * scale);
            arr[k + 7] = rand.nextFloat() * 100.0f;
        }
        b.needles = arr;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            BALLS.clear();
            return;
        }
        if (mc.isPaused()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Set<BlockPos> seen = new HashSet<>();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.KAMERTON.equals(entry.typeId())) continue;
            AABB zone = AnomalyGeometry.box(entry);
            if (zone.getCenter().distanceTo(cam) > NEAR + entry.size()) continue;
            seen.add(entry.pos());
            Ball b = BALLS.computeIfAbsent(entry.pos(), p -> new Ball());
            boolean rebuild = b.entry == null || b.entry.size() != entry.size() || b.entry.intensity() != entry.intensity();
            b.entry = entry;
            if (rebuild) build(b);
            b.prevActivity = b.activity;
            b.activity = entry.active() ? Math.min(1.0f, b.activity + 0.1f) : Math.max(0.0f, b.activity - 0.03f);
            b.bodies.clear();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, LivingEntity::isAlive)) {
                b.bodies.add(e.getBoundingBox());
            }
            if (b.ring == null || b.ring.isStopped()) {
                Ball bb = b;
                BlockPos pos = entry.pos();
                b.ring = new ZoneLoopSound(ModSounds.KAMERTON_RING.get(), zone.getCenter(),
                        () -> BALLS.get(pos) == bb, () -> 0.12 + 0.4 * bb.activity);
                mc.getSoundManager().play(b.ring);
            }
        }
        BALLS.keySet().removeIf(p -> !seen.contains(p));
    }

    public static void collect(List<Distortion.Patch> out, long now, float partial) {
        float time = (now % 72000L) + partial;
        for (Ball b : BALLS.values()) {
            if (b.entry == null) continue;
            Vec3 c = AnomalyGeometry.box(b.entry).getCenter();
            out.add(Distortion.Lens.shimmer(c, b.entry.size() / 2.0, 0.012, time * 0.03, 0.7f));
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || BALLS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick();
        float time = (mc.level.getGameTime() % 72000L) + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();

        // The glass (alpha-blended, no depth writes).
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        BufferBuilder glass = Tesselator.getInstance().getBuilder();
        glass.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        List<Vec3[]> drawn = new ArrayList<>();
        for (Ball b : BALLS.values()) {
            if (b.entry != null && !b.entry.visible()) continue;
            if (b.entry == null) continue;
            Vec3 c = AnomalyGeometry.box(b.entry).getCenter();
            float[] a = b.needles;
            for (int i = 0; i + NEEDLE - 1 < a.length; i += NEEDLE) {
                Vec3 mid0 = c.add(a[i], a[i + 1], a[i + 2]);
                Vec3 half = new Vec3(a[i + 3], a[i + 4], a[i + 5]).scale(a[i + 6] * 0.5);
                Vec3 base = mid0.subtract(half);
                Vec3 tip = mid0.add(half);
                // Leaning aside round anyone among them.
                for (AABB body : b.bodies) {
                    Vec3 mid = base.add(tip).scale(0.5);
                    Vec3 bc = body.getCenter();
                    Vec3 away = mid.subtract(bc);
                    double reach = Math.max(body.getXsize(), body.getZsize()) * 0.5 + 0.5;
                    double dy = Math.max(0.0, Math.abs(mid.y - bc.y) - body.getYsize() * 0.5);
                    double dh = Math.sqrt(away.x * away.x + away.z * away.z);
                    if (dh < reach && dy < 0.4) {
                        Vec3 push = new Vec3(away.x, 0.0, away.z).normalize().scale((reach - dh) * 0.8);
                        base = base.add(push.scale(0.4));
                        tip = tip.add(push);
                    }
                }
                drawn.add(new Vec3[]{base, tip, new Vec3(a[i + 7], 0, 0)});
                shard(glass, m, base, tip, cam, 205, 232, 245, 70);
            }
        }
        BufferUploader.drawWithShader(glass.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // The glints (additive).
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer glow = buffers.getBuffer(GlowRenderType.GLOW);
        for (Vec3[] n : drawn) {
            Vec3 base = n[0];
            Vec3 tip = n[1];
            float seed = (float) n[2].x;
            Vec3 axis = tip.subtract(base).normalize();
            Vec3 view = base.subtract(cam).normalize();
            double side = 1.0 - Math.abs(axis.dot(view));
            float twinkle = (float) Math.pow(Math.max(0.0, Math.sin(time * 0.05 + seed)), 12.0);
            int alpha = (int) (255 * Mth.clamp(0.06 + 0.3 * Math.pow(side, 6.0) + 0.6 * twinkle, 0.0, 1.0));
            if (alpha > 4) shard(glow, m, base, tip, cam, 230, 245, 255, alpha);
        }
        buffers.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    /** A thin tapering shard from base to tip, facing the camera. */
    private static void shard(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 tip, Vec3 cam, int r, int g, int bl, int a) {
        Vec3 axis = tip.subtract(base);
        Vec3 toCam = cam.subtract(base);
        Vec3 side = axis.cross(toCam);
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(0.018);
        put(vc, m, base.add(side), r, g, bl, a);
        put(vc, m, base.subtract(side), r, g, bl, a);
        put(vc, m, tip, r, g, bl, a);
        put(vc, m, tip, r, g, bl, a);
    }

    private static void put(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, Mth.clamp(a, 0, 255)).endVertex();
    }
}
