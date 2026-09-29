package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.tesla.LightningDraw;
import faygolover.zoneartifacts.config.ModClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
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
 * Electrification: arcs crawling over an entity's surface for a while, following it as it moves —
 * after a Tesla touch, an Electra hit, or the {@code /fl_zone_arts electrify} command. Purely visual.
 * <p>
 * The surface used is the entity's hitbox: the exact shape of an arbitrary entity's model isn't
 * available from outside that entity's own renderer. Arcs between two faces are routed through
 * the edge between them, so they wrap around the body instead of cutting through it. The number
 * of arcs grows with the hitbox area and with the effect's intensity (3 = standard), capped by the
 * player's {@code maxEffectIntensity}.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ElectrifyRenderer {

    private static final int REROLL_TICKS = 2;
    private static final int STANDARD_INTENSITY = 3;

    private static final class Electrify {
        int entityId;
        long startTick;
        int duration;
        int intensity;
        long seed;
    }

    private static final List<Electrify> ACTIVE = new ArrayList<>();

    private ElectrifyRenderer() {
    }

    public static void onElectrify(int entityId, int durationTicks, int delayTicks, int intensity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Electrify e = new Electrify();
        e.entityId = entityId;
        e.startTick = mc.level.getGameTime() + Math.max(0, delayTicks);
        e.duration = Math.max(1, durationTicks);
        e.intensity = Math.max(1, intensity);
        e.seed = RandomSource.create().nextLong();
        ACTIVE.add(e);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        if (ACTIVE.isEmpty()) return;

        long now = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        for (Iterator<Electrify> it = ACTIVE.iterator(); it.hasNext(); ) {
            Electrify e = it.next();
            if (now < e.startTick) continue; // still waiting for the strike bolts to connect
            float life = (now - e.startTick + partialTick) / e.duration;
            Entity entity = mc.level.getEntity(e.entityId);
            if (life >= 1.0f || entity == null || entity.isRemoved()) {
                it.remove();
                continue;
            }
            render(matrix, buffer, e, entity, life, now, partialTick, camPos);
        }

        bufferSource.endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static void render(Matrix4f matrix, VertexConsumer buffer, Electrify e, Entity entity,
                               float life, long now, float partialTick, Vec3 camPos) {
        // Fade out over the last 30%.
        int alpha = life < 0.7f ? 255 : (int) (255 * (1.0f - (life - 0.7f) / 0.3f));

        // The hitbox moved to the entity's interpolated position, so the arcs ride along smoothly.
        AABB box = entity.getBoundingBox()
                .move(entity.getPosition(partialTick).subtract(entity.position()))
                .inflate(0.03);
        double dx = box.getXsize(), dy = box.getYsize(), dz = box.getZsize();
        double area = 2 * (dx * dy + dy * dz + dx * dz);
        double intensityFactor = ModClientConfig.effective(e.intensity) / (double) STANDARD_INTENSITY;
        int links = Mth.clamp((int) Math.round((4 + area * 1.5) * intensityFactor), 2, 24);

        long bucket = now / REROLL_TICKS;
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
            Vec3 mid = projectToSurface(a.add(b).scale(0.5), box);
            Vec3[] first = LightningDraw.jittered(a, mid, rand, 3, 0.3);
            Vec3[] second = LightningDraw.jittered(mid, b, rand, 3, 0.3);
            LightningDraw.ribbon(matrix, buffer, first, 0.018f, 190, 225, 255, alpha, camPos);
            LightningDraw.ribbon(matrix, buffer, second, 0.018f, 190, 225, 255, alpha, camPos);
        }
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
