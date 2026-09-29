package faygolover.zoneartifacts.client.firefly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.client.tesla.LightningDraw;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Firefly: small lights wandering about their zone on slow, looping paths, never higher than the
 * targeting tuner above the ground, now and then dimming; each lays a faint glow on the ground under
 * it. Harmless; purely the clients'.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FireflyClient {

    private static final double NEAR = 96.0;

    private static final class Swarm {
        SyncAnomaliesPacket.Entry entry;
        double[] ground = new double[0];
        double[] prevGround = new double[0];
        float[] seeds = new float[0];
    }

    private static final Map<BlockPos, Swarm> SWARMS = new HashMap<>();

    private FireflyClient() {
    }

    private static double height(SyncAnomaliesPacket.Entry entry) {
        return entry.range() > 0.0f ? entry.range() : ModCommonConfig.FIREFLY_HEIGHT.get();
    }

    /** Where firefly {@code i} is at {@code t} (height over its ground left to the caller). */
    private static double[] path(Swarm s, int i, AABB zone, double t) {
        float seed = s.seeds[i];
        double hx = zone.getXsize() * 0.45;
        double hz = zone.getZsize() * 0.45;
        double sp = 0.006 * Math.max(0.05, s.entry.speed());
        double x = zone.getCenter().x + hx * (0.6 * Math.sin(t * sp * 1.0 + seed) + 0.4 * Math.sin(t * sp * 2.3 + seed * 1.7));
        double z = zone.getCenter().z + hz * (0.6 * Math.cos(t * sp * 0.9 + seed * 1.3) + 0.4 * Math.sin(t * sp * 1.9 + seed * 0.5));
        double y = 0.5 + 0.5 * Math.sin(t * sp * 1.6 + seed * 2.9);
        return new double[]{x, y, z};
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            SWARMS.clear();
            return;
        }
        if (mc.isPaused()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long now = level.getGameTime();
        Set<BlockPos> seen = new HashSet<>();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.FIREFLY.equals(entry.typeId())) continue;
            if (Vec3.atCenterOf(entry.pos()).distanceTo(cam) > NEAR + entry.size()) continue;
            seen.add(entry.pos());
            Swarm s = SWARMS.computeIfAbsent(entry.pos(), p -> new Swarm());
            s.entry = entry;
            int n = Mth.clamp(entry.intensity(), 1, 50);
            if (s.seeds.length != n) {
                RandomSource r = RandomSource.create(entry.pos().asLong());
                s.seeds = new float[n];
                for (int i = 0; i < n; i++) s.seeds[i] = r.nextFloat() * 100.0f;
                s.ground = new double[n];
                s.prevGround = new double[n];
                java.util.Arrays.fill(s.ground, Double.NaN);
            }
            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
            for (int i = 0; i < n; i++) {
                double[] p = path(s, i, zone, now);
                Double g = Razlom.groundY(level, p[0], p[2], zone.maxY, zone.minY - 4.0);
                double ground = g != null ? g : zone.minY;
                s.prevGround[i] = Double.isNaN(s.ground[i]) ? ground : s.ground[i];
                s.ground[i] = Double.isNaN(s.ground[i]) ? ground : s.ground[i] + (ground - s.ground[i]) * 0.2;
            }
        }
        SWARMS.keySet().removeIf(p -> !seen.contains(p));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SWARMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick();
        double t = mc.level.getGameTime() + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(GlowRenderType.GLOW);
        for (Swarm s : SWARMS.values()) {
            if (s.entry == null || s.ground.length != s.seeds.length) continue;
            AABB zone = AnomalyGeometry.centeredAabb(s.entry.pos(), s.entry.size());
            double h = height(s.entry);
            for (int i = 0; i < s.seeds.length; i++) {
                if (Double.isNaN(s.ground[i])) continue;
                double[] p = path(s, i, zone, t);
                double ground = Mth.lerp(partial, s.prevGround[i], s.ground[i]);
                double above = 0.25 + Math.max(0.0, h - 0.25) * p[1];
                Vec3 at = new Vec3(p[0], ground + above, p[2]);
                float seed = s.seeds[i];
                float blink = (float) (0.75 + 0.25 * Math.sin(t * 0.21 + seed));
                if (Math.sin(t * 0.013 + seed * 3.0) > 0.93) blink *= 0.15f;
                LightningDraw.glow(m, vc, at, 0.45, cam, 170, 255, 90, (int) (70 * blink), 14);
                LightningDraw.glow(m, vc, at, 0.09, cam, 245, 255, 190, (int) (255 * blink), 10);
                // Its glow on the ground below.
                float onGround = (float) Math.max(0.0, 1.0 - above / (h + 1.0));
                disc(vc, m, new Vec3(at.x, ground + 0.02, at.z), 0.9, (int) (40 * blink * onGround));
            }
        }
        buffers.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    private static void disc(VertexConsumer vc, Matrix4f m, Vec3 c, double r, int alpha) {
        if (alpha <= 1) return;
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2.0 * i / n;
            double a1 = Math.PI * 2.0 * (i + 1) / n;
            vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(170, 255, 90, alpha).endVertex();
            vc.vertex(m, (float) (c.x + Math.cos(a0) * r), (float) c.y, (float) (c.z + Math.sin(a0) * r)).color(170, 255, 90, 0).endVertex();
            vc.vertex(m, (float) (c.x + Math.cos(a1) * r), (float) c.y, (float) (c.z + Math.sin(a1) * r)).color(170, 255, 90, 0).endVertex();
            vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(170, 255, 90, alpha).endVertex();
        }
    }
}
