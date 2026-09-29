package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket.ArcInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Electra's ambient visual: several jagged lightning arcs, each chaining through a handful of
 * random points inside the zone, entirely client-side (purely cosmetic — the server doesn't need
 * to agree on exactly where every arc bends). Modeled on the source material's "STALKER electro
 * anomaly" look rather than a particle spray: each arc ("bundle") stays connecting the same points
 * for roughly a second, then a fresh set is rolled — and every bundle's clock runs independently,
 * so they never all flip at once. Gated on {@link SyncAnomaliesPacket.Entry#onCooldown()}, exactly
 * like the idle sound in {@code AnomalyAmbientSoundHandler}.
 * <p>
 * Rendered with vanilla's own {@link RenderType#lightning()} — the same public, additive-blended,
 * unlit RenderType the real lightning bolt entity uses — so unlike the semi-transparent highlight
 * box (which needed manual Tesselator/RenderSystem state because no matching public RenderType
 * exists for it) this needs no low-level state hacking at all.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnomalyArcRenderer {

    /** How far around the player arcs get simulated/drawn at all. */
    private static final double VISIBLE_RADIUS = 32.0;

    /** Shrinks the point-sampling volume in from the zone's true bounds so anchor points don't
     *  sit exactly on (or through) a wall the zone happens to touch. */
    private static final double VOLUME_INSET = 0.15;

    /** Segments per anchor-to-anchor link; higher = more jagged. */
    private static final int SEGMENTS_PER_LINK = 5;

    /** How far a segment's midpoints wander off the straight line, as a fraction of the link's
     *  own length. */
    private static final double JITTER_FRACTION = 0.14;

    /** How often (in ticks) the jagged path between two anchors re-rolls. The anchors themselves
     *  (which points are connected) only change when a bundle's lifetime expires — this is just
     *  the faster "crackle" of the line connecting them. */
    private static final int JITTER_REFRESH_TICKS = 2;

    private static final float HALF_WIDTH = 0.02f;

    private static final Map<Key, List<Bundle>> BUNDLES = new HashMap<>();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            BUNDLES.clear();
            return;
        }

        long now = mc.level.getGameTime();
        Vec3 playerPos = mc.player.position();
        double radiusSq = VISIBLE_RADIUS * VISIBLE_RADIUS;

        Set<Key> desired = new HashSet<>();
        List<Bundle> toRender = new ArrayList<>();
        List<Integer> colorsToRender = new ArrayList<>();

        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(mc.level.dimension())) {
            if (entry.onCooldown()) continue;

            ArcInfo arc = ClientAnomalyTypeCache.arcFor(entry.typeId());
            if (arc == null) continue;

            double dx = entry.pos().getX() + 0.5 - playerPos.x;
            double dy = entry.pos().getY() + 0.5 - playerPos.y;
            double dz = entry.pos().getZ() + 0.5 - playerPos.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

            Key key = new Key(entry.typeId(), entry.pos());
            desired.add(key);

            int size = ClientAnomalyTypeCache.sizeForLevel(entry.typeId(), entry.level());
            AABB aabb = AnomalyGeometry.centeredAabb(entry.pos(), size).deflate(VOLUME_INSET);

            List<Bundle> bundles = BUNDLES.computeIfAbsent(key, k -> new ArrayList<>());
            // Bring the bundle count up to spec; stagger a freshly-created bundle's first expiry
            // instead of starting it at "now" so a batch of brand-new anomalies doesn't have every
            // bundle change in lockstep the first time either.
            RandomSource seedSource = RandomSource.create();
            while (bundles.size() < arc.bundleCount()) {
                Bundle b = new Bundle();
                b.reroll(aabb, arc, seedSource);
                b.expiresAtTick = now - seedSource.nextInt(Math.max(1, arc.maxLifetimeTicks()));
                bundles.add(b);
            }
            while (bundles.size() > arc.bundleCount()) {
                bundles.remove(bundles.size() - 1);
            }

            for (Bundle b : bundles) {
                if (now >= b.expiresAtTick) {
                    b.reroll(aabb, arc, seedSource);
                    int lifetime = arc.minLifetimeTicks()
                            + (arc.maxLifetimeTicks() > arc.minLifetimeTicks()
                                    ? seedSource.nextInt(arc.maxLifetimeTicks() - arc.minLifetimeTicks() + 1) : 0);
                    b.expiresAtTick = now + lifetime;
                }
                toRender.add(b);
                colorsToRender.add(arc.color());
            }
        }

        BUNDLES.keySet().removeIf(k -> !desired.contains(k));
        if (toRender.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        for (int i = 0; i < toRender.size(); i++) {
            renderBundle(matrix, buffer, toRender.get(i), colorsToRender.get(i), now, camPos);
        }

        bufferSource.endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void renderBundle(Matrix4f matrix, VertexConsumer buffer, Bundle bundle, int color, long now, Vec3 camPos) {
        List<Vec3> anchors = bundle.anchors;
        if (anchors.size() < 2) return;

        long timeBucket = now / JITTER_REFRESH_TICKS;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (int i = 0; i < anchors.size() - 1; i++) {
            long segSeed = bundle.seed ^ (i * 0x9E3779B97F4A7C15L) ^ (timeBucket * 0xBF58476D1CE4E5B9L);
            RandomSource rand = RandomSource.create(segSeed);
            Vec3[] points = buildJitteredPoints(anchors.get(i), anchors.get(i + 1), rand, SEGMENTS_PER_LINK, JITTER_FRACTION);
            renderPolylineQuads(matrix, buffer, points, HALF_WIDTH, r, g, b, camPos);
        }
    }

    /** A polyline between two anchors, jittered perpendicular to the straight line between them.
     *  The jitter amplitude tapers to zero at both ends (via a sine envelope) so the path always
     *  lands exactly on its anchors no matter how jagged the middle gets. */
    private static Vec3[] buildJitteredPoints(Vec3 start, Vec3 end, RandomSource rand, int segments, double jitterFraction) {
        Vec3 dir = end.subtract(start);
        double len = dir.length();
        Vec3[] points = new Vec3[segments + 1];
        if (len < 1.0E-4) {
            for (int i = 0; i <= segments; i++) points[i] = start;
            return points;
        }

        Vec3 dirNorm = dir.scale(1.0 / len);
        Vec3 arbitrary = Math.abs(dirNorm.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp1 = dirNorm.cross(arbitrary).normalize();
        Vec3 perp2 = dirNorm.cross(perp1).normalize();

        for (int j = 0; j <= segments; j++) {
            double t = j / (double) segments;
            Vec3 base = start.add(dir.scale(t));
            if (j == 0 || j == segments) {
                points[j] = base;
            } else {
                double jitterScale = Math.sin(Math.PI * t) * len * jitterFraction;
                double j2 = (rand.nextDouble() - 0.5) * 2.0 * jitterScale;
                double j3 = (rand.nextDouble() - 0.5) * 2.0 * jitterScale;
                points[j] = base.add(perp1.scale(j2)).add(perp2.scale(j3));
            }
        }
        return points;
    }

    /** Draws each segment as a quad billboarded to face the camera around the segment's own axis
     *  (not a flat camera-plane billboard), so the ribbon reads correctly from any angle. */
    private static void renderPolylineQuads(Matrix4f matrix, VertexConsumer buffer, Vec3[] points, float halfWidth,
                                             int r, int g, int b, Vec3 camPos) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 pa = points[i];
            Vec3 pb = points[i + 1];
            Vec3 mid = pa.add(pb).scale(0.5);

            Vec3 toCam = camPos.subtract(mid);
            double toCamLen = toCam.length();
            if (toCamLen < 1.0E-4) continue;
            toCam = toCam.scale(1.0 / toCamLen);

            Vec3 segDir = pb.subtract(pa);
            double segLen = segDir.length();
            if (segLen < 1.0E-4) continue;
            segDir = segDir.scale(1.0 / segLen);

            Vec3 side = segDir.cross(toCam);
            double sideLen = side.length();
            if (sideLen < 1.0E-4) continue;
            side = side.scale(halfWidth / sideLen);

            vertex(buffer, matrix, pa.subtract(side), r, g, b);
            vertex(buffer, matrix, pa.add(side), r, g, b);
            vertex(buffer, matrix, pb.add(side), r, g, b);
            vertex(buffer, matrix, pb.subtract(side), r, g, b);
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int r, int g, int b) {
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(r, g, b, 255).endVertex();
    }

    private static final class Bundle {
        List<Vec3> anchors = List.of();
        long seed;
        long expiresAtTick;

        void reroll(AABB aabb, ArcInfo arc, RandomSource rand) {
            List<Vec3> pts = new ArrayList<>(arc.pointsPerBundle());
            for (int i = 0; i < arc.pointsPerBundle(); i++) {
                double x = lerp(rand.nextDouble(), aabb.minX, aabb.maxX);
                double y = lerp(rand.nextDouble(), aabb.minY, aabb.maxY);
                double z = lerp(rand.nextDouble(), aabb.minZ, aabb.maxZ);
                pts.add(new Vec3(x, y, z));
            }
            anchors = pts;
            seed = rand.nextLong();
        }
    }

    private static double lerp(double t, double min, double max) {
        return min + (max - min) * t;
    }

    private record Key(ResourceLocation typeId, BlockPos pos) {
    }
}
