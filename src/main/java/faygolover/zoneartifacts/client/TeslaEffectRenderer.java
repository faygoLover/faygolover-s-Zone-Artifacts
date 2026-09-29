package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws every live {@link TeslaEntity}'s visuals: a small closed-loop ball of lightning (anchors
 * sampled on its own tiny bounding box, with the first anchor repeated at the end so the polyline
 * always connects back to its start — no dangling "tails", unlike Electra's open bundle style) and
 * a bright glowing core, plus, on server-sent triggers, a block-collision starburst and arcs
 * crawling across a struck entity's own surface. Reuses {@link LightningRenderUtil} for the actual
 * jitter/billboard math — the same helpers Electra's {@code AnomalyArcRenderer} uses.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT)
public final class TeslaEffectRenderer {

    private static final int AMBIENT_ANCHORS = 5;
    private static final int SEGMENTS_PER_LINK = 4;
    private static final double JITTER_AMPLITUDE = 0.08;
    private static final float CORE_GLOW_SIZE = 0.12f;
    private static final float ARC_THICKNESS = 0.04f;
    private static final float ELECTRIFY_THICKNESS = 0.05f;
    private static final float BURST_THICKNESS = 0.05f;
    private static final int BURST_DURATION_TICKS = 8;

    private static final List<Electrify> ELECTRIFICATIONS = new ArrayList<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static int frame = 0;

    private TeslaEffectRenderer() {
    }

    public static void onElectrify(int teslaEntityId, int targetEntityId, int durationTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Electrify e = new Electrify();
        e.targetEntityId = targetEntityId;
        e.endTick = level.getGameTime() + durationTicks;
        ELECTRIFICATIONS.add(e);
    }

    public static void onBlockBurst(Vec3 point, int rayCount, double distance, int color) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        RandomSource random = RandomSource.create(point.hashCode());
        Burst burst = new Burst();
        burst.point = point;
        burst.color = color;
        burst.endTick = level.getGameTime() + BURST_DURATION_TICKS;
        for (int i = 0; i < rayCount; i++) {
            Vec3 dir = new Vec3(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
            if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 1, 0);
            dir = dir.normalize();
            burst.rays.add(point.add(dir.scale(distance)));
        }
        BURSTS.add(burst);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        frame++;
        PoseStack poseStack = event.getPoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        org.joml.Vector3f lookVec = camera.getLookVector();
        Vec3 camLook = new Vec3(lookVec.x(), lookVec.y(), lookVec.z());
        float partialTick = event.getPartialTick();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof TeslaEntity tesla)) continue;
            if (tesla.getTeslaState() == TeslaEntity.STATE_RESPAWNING) continue;
            renderTeslaArcs(level, tesla, buffer, matrix, camPos, camLook, partialTick);
        }

        renderElectrifications(level, buffer, matrix, camPos, camLook);
        renderBursts(level, buffer, matrix, camPos, camLook);

        bufferSource.endBatch(RenderType.lightning());

        // The glow core is a small filled box, drawn with its own render state — outside the
        // lightning batch above, same as AnomalyHighlightRenderer's fill boxes.
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof TeslaEntity tesla)) continue;
            if (tesla.getTeslaState() == TeslaEntity.STATE_RESPAWNING) continue;
            renderCoreGlow(poseStack, tesla, camPos, partialTick);
        }
    }

    private static void renderTeslaArcs(ClientLevel level, TeslaEntity tesla, VertexConsumer buffer, Matrix4f matrix,
                                         Vec3 camPos, Vec3 camLook, float partialTick) {
        Vec3 center = tesla.getPosition(partialTick);
        float grow = tesla.getGrowProgress();
        AABB box = new AABB(center.x - 0.4, center.y - 0.4, center.z - 0.4, center.x + 0.4, center.y + 0.4, center.z + 0.4);

        // Reseeded once per second (not every frame) so the ball's shape is semi-stable rather
        // than a formless flicker, while the jitter itself still animates every frame.
        RandomSource random = RandomSource.create(tesla.getId() * 7919L + level.getGameTime() / 20);
        List<Vec3> anchors = new ArrayList<>(LightningRenderUtil.surfacePointsOfBox(box, AMBIENT_ANCHORS, random));
        if (!anchors.isEmpty()) {
            anchors.add(anchors.get(0)); // repeat the first anchor — closes the loop, no dangling tail
        }
        if (grow < 1.0f) {
            for (int i = 0; i < anchors.size(); i++) {
                anchors.set(i, center.add(anchors.get(i).subtract(center).scale(grow)));
            }
        }

        List<Vec3> jittered = LightningRenderUtil.buildJitteredPoints(
                anchors, tesla.getId() * 7919L, frame / 2, SEGMENTS_PER_LINK, JITTER_AMPLITUDE);
        LightningRenderUtil.renderPolylineQuads(buffer, matrix, jittered, camPos, camLook, ClientTeslaTypeCache.color(), ARC_THICKNESS);
    }

    private static void renderCoreGlow(PoseStack poseStack, TeslaEntity tesla, Vec3 camPos, float partialTick) {
        Vec3 center = tesla.getPosition(partialTick).subtract(camPos);
        float size = CORE_GLOW_SIZE * Math.max(0.15f, tesla.getGrowProgress());
        AABB glowBox = new AABB(center.x - size, center.y - size, center.z - size, center.x + size, center.y + size, center.z + size);
        BoxRenderUtil.renderFillBox(poseStack, glowBox, 0xFFFFFF, 0.9f);
    }

    private static void renderElectrifications(ClientLevel level, VertexConsumer buffer, Matrix4f matrix, Vec3 camPos, Vec3 camLook) {
        long now = level.getGameTime();
        Iterator<Electrify> it = ELECTRIFICATIONS.iterator();
        while (it.hasNext()) {
            Electrify e = it.next();
            if (now >= e.endTick) {
                it.remove();
                continue;
            }
            Entity target = level.getEntity(e.targetEntityId);
            if (target == null) {
                it.remove();
                continue;
            }
            RandomSource random = RandomSource.create(e.targetEntityId * 31L + frame / 2);
            List<Vec3> anchors = LightningRenderUtil.surfacePointsOfBox(target.getBoundingBox(), 5, random);
            List<Vec3> jittered = LightningRenderUtil.buildJitteredPoints(anchors, e.targetEntityId, frame, 3, 0.06);
            LightningRenderUtil.renderPolylineQuads(buffer, matrix, jittered, camPos, camLook, ClientTeslaTypeCache.color(), ELECTRIFY_THICKNESS);
        }
    }

    private static void renderBursts(ClientLevel level, VertexConsumer buffer, Matrix4f matrix, Vec3 camPos, Vec3 camLook) {
        long now = level.getGameTime();
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst burst = it.next();
            if (now >= burst.endTick) {
                it.remove();
                continue;
            }
            for (Vec3 rayEnd : burst.rays) {
                List<Vec3> jittered = LightningRenderUtil.buildJitteredPoints(
                        List.of(burst.point, rayEnd), burst.point.hashCode(), frame, 2, 0.1);
                LightningRenderUtil.renderPolylineQuads(buffer, matrix, jittered, camPos, camLook, burst.color, BURST_THICKNESS);
            }
        }
    }

    private static final class Electrify {
        int targetEntityId;
        long endTick;
    }

    private static final class Burst {
        Vec3 point;
        int color;
        long endTick;
        final List<Vec3> rays = new ArrayList<>();
    }
}
