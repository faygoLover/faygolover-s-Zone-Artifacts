package faygolover.zoneartifacts.client.tesla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tesla effects that outlive the Tesla itself (it's already popped when these play):
 * <ul>
 *     <li><b>Burst</b> — when a Tesla pops. Hitting a block, its bolts scatter into the half-space
 *     away from the wall and stop where they meet other blocks; popping on an entity, shorter bolts
 *     fly every which way. Fades out over half a second.</li>
 *     <li><b>Electrification</b> — arcs crawling over the struck entity's surface, following it as
 *     it moves, until the second hit. It uses the entity's hitbox as the surface: the exact shape of
 *     an arbitrary entity's model isn't available from outside that entity's renderer.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeslaEffectRenderer {

    private static final int BURST_LIFE_TICKS = 10;
    private static final int ELECTRIFY_REROLL_TICKS = 2;

    private record Ray(Vec3 start, Vec3 end) {
    }

    private static final class Burst {
        final List<Ray> rays = new ArrayList<>();
        long startTick;
        long seed;
    }

    private static final class Electrify {
        int entityId;
        long startTick;
        int duration;
        long seed;
    }

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final List<Electrify> ELECTRIFIED = new ArrayList<>();

    private TeslaEffectRenderer() {
    }

    // ---- triggers (from packets) -----------------------------------------------------

    public static void onBurst(Vec3 center, @Nullable Vec3 normal) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        RandomSource rand = RandomSource.create();
        Burst burst = new Burst();
        burst.startTick = mc.level.getGameTime();
        burst.seed = rand.nextLong();

        int count = normal != null ? 6 + rand.nextInt(2) : 5 + rand.nextInt(2);
        for (int i = 0; i < count; i++) {
            Vec3 dir;
            double length;
            if (normal != null) {
                dir = normal.scale(0.8).add(randomUnit(rand));
                if (dir.dot(normal) < 0.15) dir = dir.add(normal.scale(0.3 - dir.dot(normal)));
                dir = dir.normalize();
                length = 1.5 + rand.nextDouble() * 1.5;
            } else {
                dir = randomUnit(rand);
                length = 0.8 + rand.nextDouble() * 0.8;
            }
            Vec3 end = center.add(dir.scale(length));
            BlockHitResult hit = mc.level.clip(new ClipContext(center, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
            burst.rays.add(new Ray(center, end));
        }
        BURSTS.add(burst);
    }

    public static void onElectrify(int entityId, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Electrify e = new Electrify();
        e.entityId = entityId;
        e.startTick = mc.level.getGameTime();
        e.duration = Math.max(1, durationTicks);
        e.seed = RandomSource.create().nextLong();
        ELECTRIFIED.add(e);
    }

    // ---- rendering -------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BURSTS.clear();
            ELECTRIFIED.clear();
            return;
        }
        if (BURSTS.isEmpty() && ELECTRIFIED.isEmpty()) return;

        long now = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        renderBursts(matrix, buffer, now, partialTick, camPos);
        renderElectrified(matrix, buffer, now, partialTick, camPos, mc);

        bufferSource.endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void renderBursts(Matrix4f matrix, VertexConsumer buffer, long now, float partialTick, Vec3 camPos) {
        for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
            Burst burst = it.next();
            float life = (now - burst.startTick + partialTick) / BURST_LIFE_TICKS;
            if (life >= 1.0f) {
                it.remove();
                continue;
            }
            int alpha = (int) (255 * (1.0f - life * life));
            float halfWidth = 0.03f * (1.0f - 0.4f * life);

            for (int i = 0; i < burst.rays.size(); i++) {
                Ray ray = burst.rays.get(i);
                RandomSource rand = RandomSource.create(burst.seed ^ (i * 0x9E3779B97F4A7C15L) ^ (now * 0xBF58476D1CE4E5B9L));
                Vec3[] path = LightningDraw.jittered(ray.start(), ray.end(), rand, 6, 0.2);
                LightningDraw.ribbon(matrix, buffer, path, halfWidth, 200, 228, 255, alpha, camPos);

                // A fork or two off the main bolt.
                int forks = rand.nextInt(3);
                for (int f = 0; f < forks && path.length > 2; f++) {
                    Vec3 forkStart = path[1 + rand.nextInt(path.length - 2)];
                    double forkLen = ray.start().distanceTo(ray.end()) * (0.25 + rand.nextDouble() * 0.3);
                    Vec3 forkEnd = forkStart.add(ray.end().subtract(ray.start()).normalize().scale(0.5).add(randomUnit(rand)).normalize().scale(forkLen));
                    Vec3[] forkPath = LightningDraw.jittered(forkStart, forkEnd, rand, 3, 0.25);
                    LightningDraw.ribbon(matrix, buffer, forkPath, halfWidth * 0.55f, 200, 228, 255, (int) (alpha * 0.6f), camPos);
                }
            }
        }
    }

    private static void renderElectrified(Matrix4f matrix, VertexConsumer buffer, long now, float partialTick,
                                          Vec3 camPos, Minecraft mc) {
        for (Iterator<Electrify> it = ELECTRIFIED.iterator(); it.hasNext(); ) {
            Electrify e = it.next();
            float life = (now - e.startTick + partialTick) / e.duration;
            Entity entity = mc.level.getEntity(e.entityId);
            if (life >= 1.0f || entity == null || entity.isRemoved()) {
                it.remove();
                continue;
            }
            // Fade out over the last 30%.
            int alpha = life < 0.7f ? 255 : (int) (255 * (1.0f - (life - 0.7f) / 0.3f));

            // The hitbox moved to the entity's interpolated position, so the arcs ride along smoothly.
            AABB box = entity.getBoundingBox()
                    .move(entity.getPosition(partialTick).subtract(entity.position()))
                    .inflate(0.03);
            double dx = box.getXsize(), dy = box.getYsize(), dz = box.getZsize();
            double area = 2 * (dx * dy + dy * dz + dx * dz);
            int links = Mth.clamp((int) (4 + area * 1.5), 4, 14);

            long bucket = now / ELECTRIFY_REROLL_TICKS;
            RandomSource rand = RandomSource.create(e.seed ^ (bucket * 0xBF58476D1CE4E5B9L));
            for (int i = 0; i < links; i++) {
                Vec3 a = randomOnSurface(box, rand);
                Vec3 b = a;
                for (int tries = 0; tries < 8; tries++) {
                    Vec3 candidate = randomOnSurface(box, rand);
                    double d = candidate.distanceTo(a);
                    if (d >= 0.2 && d <= 0.75) {
                        b = candidate;
                        break;
                    }
                }
                if (b == a) continue;
                // Route through a point pushed back onto the surface, so an arc between two faces
                // wraps around the edge instead of cutting through the body.
                Vec3 mid = projectToSurface(a.add(b).scale(0.5), box);
                Vec3[] first = LightningDraw.jittered(a, mid, rand, 3, 0.3);
                Vec3[] second = LightningDraw.jittered(mid, b, rand, 3, 0.3);
                LightningDraw.ribbon(matrix, buffer, first, 0.018f, 190, 225, 255, alpha, camPos);
                LightningDraw.ribbon(matrix, buffer, second, 0.018f, 190, 225, 255, alpha, camPos);
            }
        }
    }

    // ---- geometry helpers ------------------------------------------------------------

    private static Vec3 randomUnit(RandomSource rand) {
        double z = rand.nextDouble() * 2.0 - 1.0;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(1.0 - z * z);
        return new Vec3(r * Math.cos(angle), r * Math.sin(angle), z);
    }

    /** Uniform point on the surface of {@code box}, faces weighted by their area. */
    private static Vec3 randomOnSurface(AABB box, RandomSource rand) {
        double dx = box.getXsize(), dy = box.getYsize(), dz = box.getZsize();
        double ax = dy * dz, ay = dx * dz, az = dx * dy;
        double pick = rand.nextDouble() * (ax + ay + az);
        double u = rand.nextDouble(), v = rand.nextDouble();
        boolean max = rand.nextBoolean();
        if (pick < ax) {
            return new Vec3(max ? box.maxX : box.minX, box.minY + u * dy, box.minZ + v * dz);
        } else if (pick < ax + ay) {
            return new Vec3(box.minX + u * dx, max ? box.maxY : box.minY, box.minZ + v * dz);
        } else {
            return new Vec3(box.minX + u * dx, box.minY + v * dy, max ? box.maxZ : box.minZ);
        }
    }

    /** Moves a point (inside or on the box) onto the nearest face of the box. */
    private static Vec3 projectToSurface(Vec3 p, AABB box) {
        double x = Mth.clamp(p.x, box.minX, box.maxX);
        double y = Mth.clamp(p.y, box.minY, box.maxY);
        double z = Mth.clamp(p.z, box.minZ, box.maxZ);
        double toMinX = x - box.minX, toMaxX = box.maxX - x;
        double toMinY = y - box.minY, toMaxY = box.maxY - y;
        double toMinZ = z - box.minZ, toMaxZ = box.maxZ - z;
        double best = Math.min(Math.min(Math.min(toMinX, toMaxX), Math.min(toMinY, toMaxY)), Math.min(toMinZ, toMaxZ));
        if (best == toMinX) x = box.minX;
        else if (best == toMaxX) x = box.maxX;
        else if (best == toMinY) y = box.minY;
        else if (best == toMaxY) y = box.maxY;
        else if (best == toMinZ) z = box.minZ;
        else z = box.maxZ;
        return new Vec3(x, y, z);
    }
}
