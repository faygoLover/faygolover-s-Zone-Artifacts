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
 * Kamerton's look: a ball of thin glass needles in nested shells, all pointing at its middle — pale,
 * translucent, glinting as the eye moves (brightest seen side-on), twinkling here and there. They
 * lean aside round whoever is among them. A quiet high ringing, louder while something moves in it,
 * and a faint shimmer of the air.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KamertonClient {

    private static final double NEAR = 64.0;
    private static final int MAX_NEEDLES = 1500;

    private static final class Ball {
        SyncAnomaliesPacket.Entry entry;
        float[] needles = new float[0]; // dir x,y,z, base radius, length, seed
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
        List<Float> out = new ArrayList<>();
        double scale = Math.min(1.0, r / 2.5) * 0.7 + 0.3;
        int total = 0;
        for (int k = 0; k < 6 && total < MAX_NEEDLES; k++) {
            double rho = r * (1.0 - k * 0.17);
            if (rho < 0.35) break;
            int n = Mth.clamp((int) (4.0 * Math.PI * rho * rho * 2.2 * eff / 3.0), 10, 420);
            n = Math.min(n, MAX_NEEDLES - total);
            for (int i = 0; i < n; i++) {
                // A Fibonacci sphere, jittered.
                double y = 1.0 - 2.0 * (i + 0.5) / n;
                double rr = Math.sqrt(Math.max(0.0, 1.0 - y * y));
                double phi = i * 2.39996 + k * 0.7 + rand.nextDouble() * 0.2;
                Vec3 dir = new Vec3(Math.cos(phi) * rr, y, Math.sin(phi) * rr)
                        .add(rand.nextGaussian() * 0.06, rand.nextGaussian() * 0.06, rand.nextGaussian() * 0.06).normalize();
                out.add((float) dir.x);
                out.add((float) dir.y);
                out.add((float) dir.z);
                out.add((float) (rho + rand.nextGaussian() * 0.04));
                out.add((float) ((0.22 + rand.nextDouble() * 0.3) * scale));
                out.add(rand.nextFloat() * 100.0f);
            }
            total += n;
        }
        float[] arr = new float[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
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
            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
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
            Vec3 c = AnomalyGeometry.centeredAabb(b.entry.pos(), b.entry.size()).getCenter();
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
            if (b.entry == null) continue;
            Vec3 c = AnomalyGeometry.centeredAabb(b.entry.pos(), b.entry.size()).getCenter();
            float[] a = b.needles;
            for (int i = 0; i + 5 < a.length; i += 6) {
                Vec3 dir = new Vec3(a[i], a[i + 1], a[i + 2]);
                double rho = a[i + 3];
                double len = a[i + 4];
                Vec3 base = c.add(dir.scale(rho));
                Vec3 tip = c.add(dir.scale(Math.max(0.05, rho - len)));
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
                drawn.add(new Vec3[]{base, tip, new Vec3(a[i + 5], 0, 0)});
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
