package faygolover.zoneartifacts.client.fantom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.client.tesla.LightningDraw;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Phantom light: blue lights set tightly in five evenly spaced upright lines (the two outer ones
 * shorter), the row across the way the GM faced when placing it, standing on the anomaly's block.
 * Each flickers on its own like a flame or an arc, twitching a little. Seen from afar they shine;
 * coming near, they fade, and at {@code goneAt} there is nothing.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FantomClient {

    private static final double FAR = 220.0;

    private FantomClient() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        float time = (mc.level.getGameTime() % 72000L) + partial;
        double gone = ModCommonConfig.FANTOM_GONE_AT.get();
        double full = Math.max(gone + 1.0, ModCommonConfig.FANTOM_FULL_AT.get());
        PoseStack poseStack = null;
        MultiBufferSource.BufferSource buffers = null;
        VertexConsumer vc = null;
        Matrix4f m = null;
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(mc.level.dimension())) {
            if (!AnomalyTypeIds.FANTOM.equals(entry.typeId())) continue;
            Vec3 base = new Vec3(entry.pos().getX() + 0.5, entry.pos().getY(), entry.pos().getZ() + 0.5);
            double size = entry.size();
            Vec3 mid = base.add(0.0, size * 0.5, 0.0);
            double dist = mid.distanceTo(cam);
            if (dist > FAR) continue;
            float fade = (float) Mth.clamp((dist - gone) / (full - gone), 0.0, 1.0);
            fade = fade * fade * (3.0f - 2.0f * fade);
            if (fade <= 0.01f) continue;
            if (vc == null) {
                poseStack = event.getPoseStack();
                poseStack.pushPose();
                poseStack.translate(-cam.x, -cam.y, -cam.z);
                m = poseStack.last().pose();
                buffers = mc.renderBuffers().bufferSource();
                vc = buffers.getBuffer(GlowRenderType.GLOW);
            }
            double yaw = Math.toRadians(entry.yaw());
            Vec3 across = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
            int perLine = Mth.clamp(4 + entry.intensity() * 2, 3, 40);
            double spacing = size / 4.0;
            float glowScale = (float) Math.max(1.0, dist / 40.0); // stays visible far off
            int seedBase = entry.pos().hashCode();
            for (int line = 0; line < 5; line++) {
                boolean outer = line == 0 || line == 4;
                double height = size * (outer ? 0.62 : 1.0);
                int n = outer ? Math.max(2, (int) Math.round(perLine * 0.62)) : perLine;
                Vec3 foot = base.add(across.scale((line - 2) * spacing)).add(0.0, outer ? size * 0.19 : 0.0, 0.0);
                for (int k = 0; k < n; k++) {
                    float seed = seedBase * 0.013f + line * 17.3f + k * 5.1f;
                    double y = height * (k + 0.5) / n;
                    // A flame's flicker, now and then an arc's twitch.
                    float flick = 0.55f + 0.3f * Mth.sin(time * 0.37f + seed) + 0.15f * Mth.sin(time * 1.13f + seed * 2.0f);
                    boolean spark = Mth.sin(time * 0.071f + seed * 3.3f) > 0.985f;
                    if (spark) flick = 1.3f;
                    double jx = 0.02 * Mth.sin(time * 0.9f + seed) + (spark ? 0.06 * Mth.sin(time * 7.0f + seed) : 0.0);
                    double jy = 0.02 * Mth.sin(time * 0.7f + seed * 1.7f);
                    Vec3 p = foot.add(across.scale(jx)).add(0.0, y + jy, 0.0);
                    int halo = (int) (70 * fade * flick);
                    int core = (int) Math.min(255, 230 * fade * flick);
                    LightningDraw.glow(m, vc, p, 0.22 * glowScale, cam, 60, 140, 255, halo, 12);
                    LightningDraw.glow(m, vc, p, 0.06 * glowScale, cam, 190, 235, 255, core, 10);
                }
            }
        }
        if (vc != null) {
            buffers.endBatch(GlowRenderType.GLOW);
            poseStack.popPose();
        }
    }
}
