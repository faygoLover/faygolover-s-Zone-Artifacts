package faygolover.zoneartifacts.client.tesla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Tesla itself: a tight ball of lightning around a bright glowing core.
 * <p>
 * The ball is made of several <em>closed</em> loops — each a jagged ring through 5–7 points on a
 * shell around the center, the last point joined back to the first — tilted at random angles. No
 * loop has loose ends, so nothing sticks out as a "tail": it reads as one knot of discharges. Each
 * loop re-forms on its own short timer and its path crackles every tick, so the ball boils rather
 * than blinks. While spawning the whole thing grows from a point.
 */
public class TeslaRenderer extends EntityRenderer<TeslaEntity> {

    private static final ResourceLocation UNUSED_TEXTURE = new ResourceLocation(ZoneArtifacts.MODID, "textures/entity/tesla.png");

    private static final int LOOP_COUNT = 5;
    private static final double SHELL_RADIUS = 0.42;
    private static final int MIN_LOOP_LIFE = 3;
    private static final int MAX_LOOP_LIFE = 8;
    private static final int SEGMENTS_PER_LINK = 3;
    private static final double JITTER = 0.28;
    private static final float LOOP_HALF_WIDTH = 0.022f;

    private static final Map<TeslaEntity, Ball> BALLS = new WeakHashMap<>();

    public TeslaRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(TeslaEntity entity) {
        return UNUSED_TEXTURE;
    }

    @Override
    public void render(TeslaEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        TeslaEntity.State state = entity.getState();
        if (!state.isVisible()) return;

        float scale = 1.0f;
        if (state == TeslaEntity.State.SPAWNING) {
            float t = Mth.clamp(entity.clientStateAge(partialTick) / TeslaClientCache.spawnGrowTicks(), 0.0f, 1.0f);
            scale = t * t * (3.0f - 2.0f * t);
        }
        if (scale < 0.02f) return;

        // The pose stack is already at the entity's interpolated position, so everything below
        // is in entity-local coordinates, camera included.
        Vec3 camLocal = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(entity.getPosition(partialTick));
        Vec3 center = new Vec3(0.0, entity.getBbHeight() / 2.0, 0.0);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());

        long now = entity.level().getGameTime();
        Ball ball = BALLS.computeIfAbsent(entity, e -> new Ball());
        ball.update(now);

        // Loops first, glow last: the lightning render type writes depth, so a glow drawn first
        // would hide the loops on the far side of the ball.
        for (int l = 0; l < ball.loops.length; l++) {
            Loop loop = ball.loops[l];
            int n = loop.points.length;
            for (int i = 0; i < n; i++) {
                Vec3 a = center.add(loop.points[i].scale(scale));
                Vec3 b = center.add(loop.points[(i + 1) % n].scale(scale));
                RandomSource rand = RandomSource.create(loop.seed ^ (i * 0x9E3779B97F4A7C15L) ^ (now * 0xBF58476D1CE4E5B9L));
                Vec3[] path = LightningDraw.jittered(a, b, rand, SEGMENTS_PER_LINK, JITTER);
                LightningDraw.ribbon(matrix, buffer, path, LOOP_HALF_WIDTH * scale, 175, 215, 255, 255, camLocal);
            }
        }

        float pulse = 1.0f + 0.08f * Mth.sin((now + partialTick) * 0.9f);
        LightningDraw.glow(matrix, buffer, center, 0.55 * scale * pulse, camLocal, 110, 180, 255, 120, 18);
        Vec3 toCam = camLocal.subtract(center).normalize();
        LightningDraw.glow(matrix, buffer, center.add(toCam.scale(0.02)), 0.17 * scale * pulse, camLocal, 255, 255, 255, 240, 14);
    }

    /** Per-Tesla client state: the loops currently forming the ball. */
    private static final class Ball {
        final Loop[] loops = new Loop[LOOP_COUNT];
        final RandomSource random = RandomSource.create();

        void update(long now) {
            for (int i = 0; i < loops.length; i++) {
                if (loops[i] == null) {
                    loops[i] = Loop.create(random, now);
                    // Stagger first expiries so the loops never all re-form on the same tick.
                    loops[i].expiresAt = now + 1 + random.nextInt(MAX_LOOP_LIFE);
                } else if (now >= loops[i].expiresAt) {
                    loops[i] = Loop.create(random, now);
                }
            }
        }
    }

    /** One closed ring, points stored relative to the ball center at full size. */
    private static final class Loop {
        Vec3[] points;
        long seed;
        long expiresAt;

        static Loop create(RandomSource random, long now) {
            Vec3 normal = randomUnit(random);
            Vec3 helper = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 u = normal.cross(helper).normalize();
            Vec3 v = normal.cross(u).normalize();

            int count = 5 + random.nextInt(3);
            double startAngle = random.nextDouble() * Math.PI * 2.0;
            Vec3[] points = new Vec3[count];
            for (int i = 0; i < count; i++) {
                double angle = startAngle + Math.PI * 2.0 * i / count + (random.nextDouble() - 0.5) * 0.6;
                double r = SHELL_RADIUS * (0.6 + random.nextDouble() * 0.45);
                double wobble = (random.nextDouble() - 0.5) * 0.35 * SHELL_RADIUS;
                points[i] = u.scale(Math.cos(angle) * r).add(v.scale(Math.sin(angle) * r)).add(normal.scale(wobble));
            }

            Loop loop = new Loop();
            loop.points = points;
            loop.seed = random.nextLong();
            loop.expiresAt = now + MIN_LOOP_LIFE + random.nextInt(MAX_LOOP_LIFE - MIN_LOOP_LIFE + 1);
            return loop;
        }

        private static Vec3 randomUnit(RandomSource random) {
            double z = random.nextDouble() * 2.0 - 1.0;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(1.0 - z * z);
            return new Vec3(r * Math.cos(angle), r * Math.sin(angle), z);
        }
    }
}
