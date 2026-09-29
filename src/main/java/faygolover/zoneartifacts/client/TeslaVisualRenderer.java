package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.entity.TeslaEntity;
import faygolover.zoneartifacts.network.SyncTeslaTypesPacket.Info;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tesla's own visuals — entirely client-side and cosmetic, exactly like Electra's {@code
 * AnomalyArcRenderer}, but built for a single small, always-on "ball of lightning" rather than a
 * zone-spanning field:
 * <ul>
 *     <li><b>The ball</b>: a handful of small closed loops of arcs (per {@link Info#bundleCount}),
 *     each looping back to its own first point instead of dangling two free ends like Electra's
 *     open chains — this is what reads as a concentrated "clump" rather than a spread-out field —
 *     plus a bright crackling core at dead center so there's always something to look at even if
 *     every loop momentarily rerolls to the same side. While a {@link TeslaEntity} is still in its
 *     grow window ({@code ageTicks <= growTicks}) the whole thing scales up from nothing, so she
 *     visibly grows out of her spawn point instead of just popping into existence full-size.</li>
 *     <li><b>Electrify</b>: on a landed hit, a closed loop of arcs hugging the struck entity's own
 *     hitbox (an ellipsoid inscribed in its live, moving bounding box) for a fixed duration — the
 *     "electrocuted like in a cartoon" effect — driven by {@link #onElectrify}.</li>
 *     <li><b>Bump</b>: a short burst of bolts radiating outward from wherever she just hit solid
 *     terrain, driven by {@link #onBump}.</li>
 * </ul>
 * All three reuse vanilla's public, additive-blended, unlit {@link RenderType#lightning()} — same
 * choice as Electra's renderer, and for the same reason (no matching public RenderType exists for
 * a manual glow effect, but lightning's is already exactly this look).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TeslaVisualRenderer {

    private static final double VISIBLE_RADIUS = 32.0;
    private static final int SEGMENTS_PER_LINK = 4;
    private static final double JITTER_FRACTION = 0.28;
    private static final int JITTER_REFRESH_TICKS = 2;
    private static final float HALF_WIDTH = 0.022f;

    /** The glow core: a little "spark ball" of short spikes radiating from dead center, redrawn
     *  every jitter refresh so it crackles rather than sitting static. */
    private static final int CORE_SPARK_COUNT = 7;
    private static final double CORE_SPARK_LENGTH_FRACTION = 0.55;
    private static final float CORE_HALF_WIDTH = 0.05f;

    private static final int ELECTRIFY_POINTS_PER_LOOP = 6;
    private static final float ELECTRIFY_HALF_WIDTH = 0.028f;

    private static final float BUMP_HALF_WIDTH = 0.03f;

    private static final Map<Integer, Ball> BALLS = new HashMap<>();
    private static final Map<Integer, Electrify> ELECTRIFIES = new HashMap<>();
    private static final List<Bump> BUMPS = new ArrayList<>();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            BALLS.clear();
            ELECTRIFIES.clear();
            BUMPS.clear();
            return;
        }

        long now = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 playerPos = mc.player.position();
        double radiusSq = VISIBLE_RADIUS * VISIBLE_RADIUS;

        Set<Integer> desiredBalls = new HashSet<>();
        List<BallJob> ballJobs = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof TeslaEntity tesla)) continue;

            Vec3 pos = tesla.getPosition(partialTick);
            if (pos.distanceToSqr(playerPos) > radiusSq) continue;

            Info info = ClientTeslaTypeCache.get(tesla.typeId());
            if (info == null) continue;

            desiredBalls.add(tesla.getId());
            Ball ball = BALLS.computeIfAbsent(tesla.getId(), id -> new Ball());

            if (ball.loops.size() != Math.max(1, info.bundleCount()) || now >= ball.expiresAtTick) {
                ball.reroll(info, RandomSource.create());
                int lifetime = info.minLifetimeTicks()
                        + (info.maxLifetimeTicks() > info.minLifetimeTicks()
                                ? RandomSource.create().nextInt(info.maxLifetimeTicks() - info.minLifetimeTicks() + 1) : 0);
                ball.expiresAtTick = now + Math.max(1, lifetime);
            }

            double growFraction = 1.0;
            if (info.growTicks() > 0 && tesla.ageTicks() <= info.growTicks()) {
                growFraction = Mth.clamp(tesla.ageTicks() / (double) info.growTicks(), 0.0, 1.0);
            }

            ballJobs.add(new BallJob(pos, ball, info.color(), info.radius() * growFraction));
        }

        BALLS.keySet().removeIf(id -> !desiredBalls.contains(id));

        pruneExpiredElectrifies(now);
        BUMPS.removeIf(b -> now >= b.expiresAtTick);

        if (ballJobs.isEmpty() && ELECTRIFIES.isEmpty() && BUMPS.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        for (BallJob job : ballJobs) {
            renderBall(matrix, buffer, job, now, camPos);
        }
        renderElectrifies(matrix, buffer, now, camPos, mc, partialTick);
        renderBumps(matrix, buffer, now, camPos);

        bufferSource.endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    /**
     * Called from {@code TeslaElectrifyPacket.handle} the instant a Tesla lands a hit: wraps the
     * struck entity in a closed loop of arcs across its own hitbox for {@code durationTicks},
     * refreshed every frame from the target's live (possibly still-moving) position and size.
     */
    public static void onElectrify(int targetEntityId, int durationTicks, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = mc.level.getGameTime();
        Electrify electrify = new Electrify();
        electrify.color = color;
        electrify.seed = RandomSource.create().nextLong();
        electrify.expiresAtTick = now + durationTicks;

        RandomSource rand = RandomSource.create(electrify.seed);
        List<Vec3> dirs = new ArrayList<>(ELECTRIFY_POINTS_PER_LOOP);
        for (int i = 0; i < ELECTRIFY_POINTS_PER_LOOP; i++) {
            dirs.add(randomUnitVector(rand));
        }
        electrify.normalizedAnchors = dirs;

        ELECTRIFIES.put(targetEntityId, electrify);
    }

    /**
     * Called from {@code TeslaBumpPacket.handle} the instant a Tesla hits solid terrain: a short
     * burst of {@code boltCount} bolts radiating outward from the impact point.
     */
    public static void onBump(Vec3 pos, int color, int boltCount, double reach, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = mc.level.getGameTime();
        Bump bump = new Bump();
        bump.pos = pos;
        bump.color = color;
        bump.reach = reach;
        bump.seed = RandomSource.create().nextLong();
        bump.spawnTick = now;
        bump.expiresAtTick = now + Math.max(1, durationTicks);

        RandomSource rand = RandomSource.create(bump.seed);
        List<Vec3> dirs = new ArrayList<>(Math.max(1, boltCount));
        for (int i = 0; i < Math.max(1, boltCount); i++) {
            dirs.add(randomUnitVector(rand));
        }
        bump.directions = dirs;

        BUMPS.add(bump);
    }

    private static void pruneExpiredElectrifies(long now) {
        Iterator<Map.Entry<Integer, Electrify>> it = ELECTRIFIES.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue().expiresAtTick) it.remove();
        }
    }

    // ---- rendering ---------------------------------------------------------

    private static void renderBall(Matrix4f matrix, VertexConsumer buffer, BallJob job, long now, Vec3 camPos) {
        if (job.radius <= 1.0E-4) return;

        long timeBucket = now / JITTER_REFRESH_TICKS;
        int r = (job.color >> 16) & 0xFF;
        int g = (job.color >> 8) & 0xFF;
        int b = job.color & 0xFF;

        for (int loopIdx = 0; loopIdx < job.ball.loops.size(); loopIdx++) {
            List<Vec3> dirs = job.ball.loops.get(loopIdx);
            int count = dirs.size();
            if (count < 2) continue;

            for (int i = 0; i < count; i++) {
                Vec3 a = job.center.add(dirs.get(i).scale(job.radius));
                Vec3 bPoint = job.center.add(dirs.get((i + 1) % count).scale(job.radius));

                long segSeed = job.ball.seed ^ (loopIdx * 0xD1B54A32D192ED03L) ^ (i * 0x9E3779B97F4A7C15L)
                        ^ (timeBucket * 0xBF58476D1CE4E5B9L);
                RandomSource rand = RandomSource.create(segSeed);
                Vec3[] points = buildJitteredPoints(a, bPoint, rand, SEGMENTS_PER_LINK, JITTER_FRACTION * job.radius);
                renderPolylineQuads(matrix, buffer, points, HALF_WIDTH, r, g, b, camPos);
            }
        }

        // Bright core crackle: a handful of short spikes from dead center, blended toward white so
        // it reads as brighter than the loops around it without needing a separate texture.
        int cr = Math.min(255, r + (255 - r) / 2);
        int cg = Math.min(255, g + (255 - g) / 2);
        int cb = Math.min(255, b + (255 - b) / 2);
        long coreSeed = job.ball.seed ^ 0x27D4EB2F165667C5L ^ (timeBucket * 0x9E3779B97F4A7C15L);
        RandomSource coreRand = RandomSource.create(coreSeed);
        double sparkLen = job.radius * CORE_SPARK_LENGTH_FRACTION;
        for (int i = 0; i < CORE_SPARK_COUNT; i++) {
            Vec3 dir = randomUnitVector(coreRand);
            Vec3[] points = new Vec3[] {job.center, job.center.add(dir.scale(sparkLen))};
            renderPolylineQuads(matrix, buffer, points, CORE_HALF_WIDTH, cr, cg, cb, camPos);
        }
    }

    private static void renderElectrifies(Matrix4f matrix, VertexConsumer buffer, long now, Vec3 camPos,
                                           Minecraft mc, float partialTick) {
        if (ELECTRIFIES.isEmpty()) return;

        long timeBucket = now / JITTER_REFRESH_TICKS;
        for (Map.Entry<Integer, Electrify> entry : ELECTRIFIES.entrySet()) {
            Entity target = mc.level.getEntity(entry.getKey());
            if (target == null) continue;
            Electrify electrify = entry.getValue();

            Vec3 basePos = target.getPosition(partialTick);
            double halfWidth = target.getBbWidth() / 2.0;
            double halfHeight = target.getBbHeight() / 2.0;
            Vec3 center = basePos.add(0, halfHeight, 0);

            int r = (electrify.color >> 16) & 0xFF;
            int g = (electrify.color >> 8) & 0xFF;
            int b = electrify.color & 0xFF;

            List<Vec3> dirs = electrify.normalizedAnchors;
            int count = dirs.size();
            for (int i = 0; i < count; i++) {
                Vec3 a = toSurfacePoint(center, dirs.get(i), halfWidth, halfHeight);
                Vec3 bPoint = toSurfacePoint(center, dirs.get((i + 1) % count), halfWidth, halfHeight);

                long segSeed = electrify.seed ^ (i * 0x9E3779B97F4A7C15L) ^ (timeBucket * 0xBF58476D1CE4E5B9L);
                RandomSource rand = RandomSource.create(segSeed);
                double linkLen = a.distanceTo(bPoint);
                Vec3[] points = buildJitteredPoints(a, bPoint, rand, SEGMENTS_PER_LINK, JITTER_FRACTION * Math.max(0.1, linkLen));
                renderPolylineQuads(matrix, buffer, points, ELECTRIFY_HALF_WIDTH, r, g, b, camPos);
            }
        }
    }

    private static Vec3 toSurfacePoint(Vec3 center, Vec3 dir, double halfWidth, double halfHeight) {
        return center.add(dir.x * halfWidth, dir.y * halfHeight, dir.z * halfWidth);
    }

    private static void renderBumps(Matrix4f matrix, VertexConsumer buffer, long now, Vec3 camPos) {
        if (BUMPS.isEmpty()) return;

        for (Bump bump : BUMPS) {
            double span = Math.max(1, bump.expiresAtTick - bump.spawnTick);
            double t = Mth.clamp((now - bump.spawnTick) / span, 0.0, 1.0);
            // Bolts snap out fast and hold, rather than growing linearly the whole duration.
            double reachFraction = Math.min(1.0, t * 2.5);

            int r = (bump.color >> 16) & 0xFF;
            int g = (bump.color >> 8) & 0xFF;
            int b = bump.color & 0xFF;

            for (int i = 0; i < bump.directions.size(); i++) {
                Vec3 dir = bump.directions.get(i);
                Vec3 end = bump.pos.add(dir.scale(bump.reach * reachFraction));

                long segSeed = bump.seed ^ (i * 0x9E3779B97F4A7C15L);
                RandomSource rand = RandomSource.create(segSeed);
                Vec3[] points = buildJitteredPoints(bump.pos, end, rand, SEGMENTS_PER_LINK, JITTER_FRACTION * bump.reach);
                renderPolylineQuads(matrix, buffer, points, BUMP_HALF_WIDTH, r, g, b, camPos);
            }
        }
    }

    // ---- shared geometry helpers (deliberately independent of AnomalyArcRenderer's private
    // copies — Tesla's ball/electrify/bump shapes are different enough, and small enough, that
    // sharing code would cost more clarity than it'd save) ----------------------------------

    private static Vec3[] buildJitteredPoints(Vec3 start, Vec3 end, RandomSource rand, int segments, double jitterAbs) {
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
                double jitterScale = Math.sin(Math.PI * t) * jitterAbs;
                double j2 = (rand.nextDouble() - 0.5) * 2.0 * jitterScale;
                double j3 = (rand.nextDouble() - 0.5) * 2.0 * jitterScale;
                points[j] = base.add(perp1.scale(j2)).add(perp2.scale(j3));
            }
        }
        return points;
    }

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

    /** A uniformly-distributed random point on the unit sphere, used both for anchors on the ball
     *  and for the directions arcs/spikes/bolts fan out along. */
    private static Vec3 randomUnitVector(RandomSource rand) {
        double z = rand.nextDouble() * 2.0 - 1.0;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        double radius = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new Vec3(radius * Math.cos(angle), z, radius * Math.sin(angle));
    }

    // ---- state --------------------------------------------------------------

    private static final class Ball {
        /** One list of unit-sphere directions per closed loop; anchor {@code i} connects to
         *  {@code i + 1}, and the last one wraps back to the first — no dangling ends. */
        List<List<Vec3>> loops = List.of();
        long seed;
        long expiresAtTick;

        void reroll(Info info, RandomSource rand) {
            int loopCount = Math.max(1, info.bundleCount());
            int pointsPerLoop = Math.max(3, info.pointsPerBundle());
            List<List<Vec3>> newLoops = new ArrayList<>(loopCount);
            for (int i = 0; i < loopCount; i++) {
                List<Vec3> dirs = new ArrayList<>(pointsPerLoop);
                for (int j = 0; j < pointsPerLoop; j++) {
                    dirs.add(randomUnitVector(rand));
                }
                newLoops.add(dirs);
            }
            loops = newLoops;
            seed = rand.nextLong();
        }
    }

    private static final class BallJob {
        final Vec3 center;
        final Ball ball;
        final int color;
        final double radius;

        BallJob(Vec3 center, Ball ball, int color, double radius) {
            this.center = center;
            this.ball = ball;
            this.color = color;
            this.radius = radius;
        }
    }

    private static final class Electrify {
        List<Vec3> normalizedAnchors = List.of();
        int color;
        long seed;
        long expiresAtTick;
    }

    private static final class Bump {
        Vec3 pos = Vec3.ZERO;
        List<Vec3> directions = List.of();
        int color;
        double reach;
        long seed;
        long spawnTick;
        long expiresAtTick;
    }

    private TeslaVisualRenderer() {
    }
}
