package faygolover.zoneartifacts.client.tesla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.config.ModClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
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
 * The burst of a popping Tesla (it's already gone when this plays). Hitting a block, its bolts
 * scatter into the half-space away from the wall and stop where they meet other blocks; popping on
 * an entity, shorter bolts fly every which way. Bolt length scales with the Tesla's size, the
 * number of bolts with its intensity (capped by the player's {@code maxEffectIntensity}). Fades
 * out over half a second. The electrification of a struck entity is drawn by {@code ElectrifyRenderer}.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeslaEffectRenderer {

    private static final int BURST_LIFE_TICKS = 10;
    private static final int STANDARD_INTENSITY = 3;

    private record Ray(Vec3 start, Vec3 end) {
    }

    private static final class Burst {
        final List<Ray> rays = new ArrayList<>();
        long startTick;
        long seed;
        float width;
    }

    private static final List<Burst> BURSTS = new ArrayList<>();

    private TeslaEffectRenderer() {
    }

    public static void onBurst(Vec3 center, @Nullable Vec3 normal, float size, int intensity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        RandomSource rand = RandomSource.create();
        Burst burst = new Burst();
        burst.startTick = mc.level.getGameTime();
        burst.seed = rand.nextLong();
        burst.width = 0.03f * (float) Math.sqrt(Math.max(0.1f, size));

        double factor = ModClientConfig.effective(intensity) / (double) STANDARD_INTENSITY;
        int base = normal != null ? 6 + rand.nextInt(2) : 5 + rand.nextInt(2);
        int count = Mth.clamp((int) Math.round(base * factor), 2, 24);
        for (int i = 0; i < count; i++) {
            Vec3 dir;
            double length;
            if (normal != null) {
                dir = normal.scale(0.8).add(randomUnit(rand));
                if (dir.dot(normal) < 0.15) dir = dir.add(normal.scale(0.3 - dir.dot(normal)));
                dir = dir.normalize();
                length = (1.5 + rand.nextDouble() * 1.5) * size;
            } else {
                dir = randomUnit(rand);
                length = (0.8 + rand.nextDouble() * 0.8) * size;
            }
            Vec3 end = center.add(dir.scale(length));
            BlockHitResult hit = mc.level.clip(new ClipContext(center, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
            burst.rays.add(new Ray(center, end));
        }
        BURSTS.add(burst);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BURSTS.clear();
            return;
        }
        if (BURSTS.isEmpty()) return;

        long now = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());

        for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
            Burst burst = it.next();
            float life = (now - burst.startTick + partialTick) / BURST_LIFE_TICKS;
            if (life >= 1.0f) {
                it.remove();
                continue;
            }
            int alpha = (int) (255 * (1.0f - life * life));
            float halfWidth = burst.width * (1.0f - 0.4f * life);

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

        bufferSource.endBatch(RenderType.lightning());
        poseStack.popPose();
    }

    private static Vec3 randomUnit(RandomSource rand) {
        double z = rand.nextDouble() * 2.0 - 1.0;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(1.0 - z * z);
        return new Vec3(r * Math.cos(angle), r * Math.sin(angle), z);
    }
}
