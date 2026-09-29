package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Draws every anomaly's ambient lightning (several independent, asynchronously-refreshing jagged
 * arcs anchored to real block-collision faces inside the zone) and, on a server-sent
 * {@link #onStrike}, a short-windup real lightning strike from the zone onto whatever it hit.
 * <p>
 * Both are purely visual and entirely client-driven: the server never simulates an arc, it only
 * tells this class (via synced type config + cooldown flips + strike packets) when something
 * should be drawn.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT)
public final class AnomalyArcRenderer {

    private static final int STRIKE_WINDUP_TICKS = 4;
    private static final int STRIKE_DURATION_TICKS = 6;
    private static final float ARC_THICKNESS = 0.05f;
    private static final float STRIKE_THICKNESS = 0.08f;
    private static final int SEGMENTS_PER_LINK = 6;
    private static final double JITTER_AMPLITUDE = 0.18;

    private static final Map<AmbientKey, List<Bundle>> AMBIENT = new HashMap<>();
    private static final List<Strike> STRIKES = new ArrayList<>();
    private static int frame = 0;

    private AnomalyArcRenderer() {
    }

    public static void onStrike(ResourceLocation dimension, ResourceLocation typeId, BlockPos pos, int targetEntityId) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !level.dimension().location().equals(dimension)) return;

        ClientAnomalyCache.Entry entry = findEntry(typeId, pos);
        SyncAnomalyTypeShapesPacket.TypeShape shape = ClientAnomalyTypeCache.get(typeId);
        if (entry == null || shape == null) return;

        int size = ClientAnomalyTypeCache.sizeForLevel(shape, entry.level());
        AABB aabb = AnomalyGeometry.centeredAabb(pos, size);
        RandomSource random = RandomSource.create((long) pos.hashCode() * 31 + level.getGameTime());
        List<Vec3> anchors = LightningRenderUtil.findSurfacePoints(level, aabb, 3, random);

        long now = level.getGameTime();
        Strike strike = new Strike();
        strike.typeId = typeId;
        strike.targetEntityId = targetEntityId;
        strike.anchorPoints = anchors;
        strike.windupEndsAtTick = now + STRIKE_WINDUP_TICKS;
        strike.expiresAtTick = now + STRIKE_WINDUP_TICKS + STRIKE_DURATION_TICKS;
        STRIKES.add(strike);
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

        // Reuses vanilla's own public RenderType.lightning() rather than a custom RenderType (the
        // RenderStateShard constants a custom one would need are protected in 1.20.1) — going
        // through a real MultiBufferSource, exactly like vanilla's own LightningBoltRenderer does,
        // rather than driving the RenderType by hand, so its render state is always set up and
        // torn down correctly.
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        renderAmbient(level, buffer, matrix, camPos, camLook);
        renderStrikes(level, buffer, matrix, camPos, camLook, event.getPartialTick());

        bufferSource.endBatch(RenderType.lightning());
    }

    // ---- ambient arcs ------------------------------------------------------

    private static void renderAmbient(ClientLevel level, VertexConsumer buffer, Matrix4f matrix, Vec3 camPos, Vec3 camLook) {
        long now = level.getGameTime();

        for (ClientAnomalyCache.Entry entry : ClientAnomalyCache.all()) {
            if (ClientAnomalyCache.isOnCooldown(entry.typeId(), entry.pos())) continue;

            SyncAnomalyTypeShapesPacket.TypeShape shape = ClientAnomalyTypeCache.get(entry.typeId());
            if (shape == null || shape.arc() == null) continue;
            SyncAnomalyTypeShapesPacket.ArcShape arc = shape.arc();

            AmbientKey key = new AmbientKey(entry.typeId(), entry.pos());
            List<Bundle> bundles = AMBIENT.computeIfAbsent(key, k -> new ArrayList<>());
            while (bundles.size() < arc.bundleCount()) {
                bundles.add(new Bundle());
            }
            while (bundles.size() > arc.bundleCount()) {
                bundles.remove(bundles.size() - 1);
            }

            int size = ClientAnomalyTypeCache.sizeForLevel(shape, entry.level());
            AABB aabb = AnomalyGeometry.centeredAabb(entry.pos(), size);

            for (Bundle bundle : bundles) {
                if (bundle.anchors.isEmpty() || now >= bundle.expiresAtTick) {
                    RandomSource random = RandomSource.create(entry.pos().hashCode() * 31L + now + bundle.hashCode());
                    bundle.anchors = buildBundleAnchors(level, aabb, arc.pointsPerBundle(), random);
                    bundle.expiresAtTick = now + arc.minLifetimeTicks()
                            + random.nextInt(Math.max(1, arc.maxLifetimeTicks() - arc.minLifetimeTicks() + 1));
                    bundle.seed = random.nextLong();
                }

                List<Vec3> jittered = LightningRenderUtil.buildJitteredPoints(
                        bundle.anchors, bundle.seed, frame / 3, SEGMENTS_PER_LINK, JITTER_AMPLITUDE);
                LightningRenderUtil.renderPolylineQuads(buffer, matrix, jittered, camPos, camLook, arc.color(), ARC_THICKNESS);
            }
        }
    }

    /** Closed-loop-agnostic anchor list: consecutive surface points, with an occasional "bulge" point for volume. */
    private static List<Vec3> buildBundleAnchors(ClientLevel level, AABB aabb, int count, RandomSource random) {
        List<Vec3> anchors = new ArrayList<>(LightningRenderUtil.findSurfacePoints(level, aabb, count, random));
        if (anchors.size() >= 2 && random.nextFloat() < 0.4f) {
            int i = random.nextInt(anchors.size() - 1);
            Vec3 a = anchors.get(i);
            Vec3 b = anchors.get(i + 1);
            Vec3 mid = a.add(b).scale(0.5).add(0, 0.15 + random.nextDouble() * 0.25, 0);
            anchors.add(i + 1, mid);
        }
        return anchors;
    }

    // ---- strikes ------------------------------------------------------------

    private static void renderStrikes(ClientLevel level, VertexConsumer buffer, Matrix4f matrix, Vec3 camPos, Vec3 camLook, float partialTick) {
        long now = level.getGameTime();

        Iterator<Strike> it = STRIKES.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            if (now >= strike.expiresAtTick) {
                it.remove();
                continue;
            }
            if (now < strike.windupEndsAtTick) {
                continue; // still winding up — nothing drawn yet
            }

            Entity target = level.getEntity(strike.targetEntityId);
            if (target == null) {
                it.remove();
                continue;
            }

            SyncAnomalyTypeShapesPacket.TypeShape shape = ClientAnomalyTypeCache.get(strike.typeId);
            int color = (shape != null && shape.arc() != null) ? shape.arc().color() : 0xFFFFFF;

            Vec3 targetPos = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
            for (Vec3 anchor : strike.anchorPoints) {
                List<Vec3> jittered = LightningRenderUtil.buildJitteredPoints(
                        List.of(anchor, targetPos), strike.hashCode(), frame, SEGMENTS_PER_LINK, JITTER_AMPLITUDE);
                LightningRenderUtil.renderPolylineQuads(buffer, matrix, jittered, camPos, camLook, color, STRIKE_THICKNESS);
            }
        }
    }

    private static ClientAnomalyCache.Entry findEntry(ResourceLocation typeId, BlockPos pos) {
        for (ClientAnomalyCache.Entry entry : ClientAnomalyCache.ofType(typeId)) {
            if (entry.pos().equals(pos)) return entry;
        }
        return null;
    }

    private record AmbientKey(ResourceLocation typeId, BlockPos pos) {
    }

    private static final class Bundle {
        List<Vec3> anchors = List.of();
        long expiresAtTick = 0;
        long seed = 0;
    }

    private static final class Strike {
        ResourceLocation typeId;
        int targetEntityId;
        List<Vec3> anchorPoints;
        long windupEndsAtTick;
        long expiresAtTick;
    }
}
