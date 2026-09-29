package faygolover.zoneartifacts.client.tesla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.tesla.Tesla;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Tesla itself: a tight ball of lightning around a bright glowing core, {@code size} blocks
 * across.
 * <ul>
 *     <li><b>Loops</b> — as many as the Tesla's intensity (capped by the player's
 *     {@code maxEffectIntensity}): each a closed jagged ring through 5–7 points on a shell around
 *     the center, tilted at random. No loose ends, so it reads as one knot of discharges. Each loop
 *     re-forms on its own short timer and crackles every tick.</li>
 *     <li><b>Grabbing arcs</b> — now and then a discharge grows from the ball's center onto a nearby
 *     block surface (within twice the ball's radius) and holds on for a second or so, stretching
 *     behind the Tesla if it flies on. Found by a few short raycasts, only when a slot is due to
 *     latch again, so they cost next to nothing. Purely visual.</li>
 * </ul>
 * While spawning the whole thing grows from a point.
 */
public class TeslaRenderer extends EntityRenderer<TeslaEntity> {

    private static final ResourceLocation UNUSED_TEXTURE = new ResourceLocation(ZoneArtifacts.MODID, "textures/entity/tesla.png");

    /** Shell radius at size 1 (the ball is 1 block across, i.e. radius 0.5). */
    private static final double SHELL_RADIUS = 0.42;
    private static final int MIN_LOOP_LIFE = 3;
    private static final int MAX_LOOP_LIFE = 8;
    private static final int SEGMENTS_PER_LINK = 3;
    private static final double JITTER = 0.28;
    private static final float LOOP_HALF_WIDTH = 0.022f;

    /** Grabbing arcs appear rarely but linger: each lives 12–24 ticks, then its slot rests 8–20
     *  ticks before latching on again. */
    private static final int MIN_GRAB_LIFE = 12;
    private static final int MAX_GRAB_LIFE = 24;
    private static final int MIN_GRAB_PAUSE = 8;
    private static final int MAX_GRAB_PAUSE = 20;
    private static final int GRAB_SEARCH_TRIES = 4;

    /** A latched arc stays even after the Tesla flies out of the latching radius, stretching
     *  behind it; only past this many radii (say, a very fast Tesla) is it dropped early. */
    private static final double GRAB_MAX_STRETCH = 4.0;

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

        float grow = 1.0f;
        if (state == TeslaEntity.State.SPAWNING) {
            float t = Mth.clamp(entity.clientStateAge(partialTick) / Tesla.SPAWN_GROW_TICKS, 0.0f, 1.0f);
            grow = t * t * (3.0f - 2.0f * t);
        }
        if (grow < 0.02f) return;

        float size = entity.getSize();
        double scale = grow * size;
        float width = LOOP_HALF_WIDTH * (float) Math.sqrt(size) * grow;
        int intensity = ModClientConfig.effective(entity.getIntensity());

        // The pose stack is already at the entity's interpolated position, so everything below
        // is in entity-local coordinates, camera included.
        Vec3 lerpPos = entity.getPosition(partialTick);
        Vec3 camLocal = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(lerpPos);
        Vec3 center = new Vec3(0.0, entity.getBbHeight() / 2.0, 0.0);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());

        long now = entity.level().getGameTime();
        Ball ball = BALLS.computeIfAbsent(entity, e -> new Ball());
        ball.update(entity, now, intensity, state != TeslaEntity.State.SPAWNING);

        // Loops and grabbing arcs first, glow last: the lightning render type writes depth, so a
        // glow drawn first would hide the loops on the far side of the ball.
        for (Loop loop : ball.loops) {
            int n = loop.points.length;
            for (int i = 0; i < n; i++) {
                Vec3 a = center.add(loop.points[i].scale(scale));
                Vec3 b = center.add(loop.points[(i + 1) % n].scale(scale));
                RandomSource rand = RandomSource.create(loop.seed ^ (i * 0x9E3779B97F4A7C15L) ^ (now * 0xBF58476D1CE4E5B9L));
                Vec3[] path = LightningDraw.jittered(a, b, rand, SEGMENTS_PER_LINK, JITTER);
                LightningDraw.ribbon(matrix, buffer, path, width, 175, 215, 255, 255, camLocal);
            }
        }

        for (Grab grab : ball.grabs) {
            if (grab == null) continue;
            // Grows from the very center of the ball, which it keeps following as the Tesla moves,
            // while the other end stays fixed on the block it latched onto.
            Vec3 hitLocal = grab.hitWorld.subtract(lerpPos);
            if (hitLocal.distanceToSqr(center) < 1.0E-6) continue;
            float life = Mth.clamp((float) (now - grab.startTick + partialTick) / (grab.expiresAt - grab.startTick), 0.0f, 1.0f);
            int alpha = life < 0.7f ? 255 : (int) (255 * (1.0f - (life - 0.7f) / 0.3f));
            RandomSource rand = RandomSource.create(grab.seed ^ (now * 0xBF58476D1CE4E5B9L));
            int segments = Mth.clamp((int) Math.ceil(hitLocal.distanceTo(center) * 3), 4, 12);
            Vec3[] path = LightningDraw.jittered(center, hitLocal, rand, segments, 0.18);
            LightningDraw.ribbon(matrix, buffer, path, width * 0.8f, 185, 222, 255, alpha, camLocal);
        }

        float pulse = 1.0f + 0.08f * Mth.sin((now + partialTick) * 0.9f);
        LightningDraw.glow(matrix, buffer, center, 0.55 * scale * pulse, camLocal, 110, 180, 255, 120, 18);
        Vec3 toCam = camLocal.subtract(center).normalize();
        LightningDraw.glow(matrix, buffer, center.add(toCam.scale(0.02 * size)), 0.17 * scale * pulse, camLocal, 255, 255, 255, 240, 14);
    }

    /** Per-Tesla client state: the loops forming the ball and the arcs latched onto blocks. */
    private static final class Ball {
        final List<Loop> loops = new ArrayList<>();
        Grab[] grabs = new Grab[0];
        long[] nextSearch = new long[0];
        final RandomSource random = RandomSource.create();
        long lastUpdate = Long.MIN_VALUE;

        void update(TeslaEntity entity, long now, int intensity, boolean grabbing) {
            if (now == lastUpdate) return;
            lastUpdate = now;

            while (loops.size() < intensity) {
                Loop loop = Loop.create(random, now);
                // Stagger first expiries so the loops never all re-form on the same tick.
                loop.expiresAt = now + 1 + random.nextInt(MAX_LOOP_LIFE);
                loops.add(loop);
            }
            while (loops.size() > intensity) {
                loops.remove(loops.size() - 1);
            }
            for (int i = 0; i < loops.size(); i++) {
                if (now >= loops.get(i).expiresAt) loops.set(i, Loop.create(random, now));
            }

            // Roughly one grabbing arc per two points of intensity: 2 at the default 3.
            int grabCount = grabbing ? Math.max(1, (intensity + 1) / 2) : 0;
            if (grabs.length != grabCount) {
                grabs = new Grab[grabCount];
                nextSearch = new long[grabCount];
                // Stagger the slots so the arcs don't all latch on at the same moment.
                for (int i = 0; i < grabCount; i++) nextSearch[i] = now + random.nextInt(MAX_GRAB_PAUSE + 1);
            }
            if (grabCount == 0) return;

            Vec3 center = entity.center();
            double reach = entity.getSize(); // twice the ball's radius (size / 2)
            for (int i = 0; i < grabs.length; i++) {
                Grab g = grabs[i];
                if (g != null) {
                    if (now >= g.expiresAt || g.hitWorld.distanceTo(center) > reach * GRAB_MAX_STRETCH) {
                        grabs[i] = null;
                        nextSearch[i] = now + MIN_GRAB_PAUSE + random.nextInt(MAX_GRAB_PAUSE - MIN_GRAB_PAUSE + 1);
                    }
                } else if (now >= nextSearch[i]) {
                    grabs[i] = findGrab(entity, center, reach, now);
                    if (grabs[i] == null) nextSearch[i] = now + 2; // nothing solid in reach — look again shortly
                }
            }
        }

        private Grab findGrab(TeslaEntity entity, Vec3 center, double reach, long now) {
            for (int attempt = 0; attempt < GRAB_SEARCH_TRIES; attempt++) {
                Vec3 end = center.add(Loop.randomUnit(random).scale(reach));
                BlockHitResult hit = entity.level().clip(new ClipContext(center, end,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
                if (hit.getType() == HitResult.Type.MISS) continue;
                Grab g = new Grab();
                g.hitWorld = hit.getLocation();
                g.startTick = now;
                g.expiresAt = now + MIN_GRAB_LIFE + random.nextInt(MAX_GRAB_LIFE - MIN_GRAB_LIFE + 1);
                g.seed = random.nextLong();
                return g;
            }
            return null; // nothing solid nearby this tick — try again next tick
        }
    }

    /** A discharge from the ball onto a block surface, fixed to its world hit point. */
    private static final class Grab {
        Vec3 hitWorld;
        long startTick;
        long expiresAt;
        long seed;
    }

    /** One closed ring, points stored relative to the ball center at size 1. */
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

        static Vec3 randomUnit(RandomSource random) {
            double z = random.nextDouble() * 2.0 - 1.0;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(1.0 - z * z);
            return new Vec3(r * Math.cos(angle), r * Math.sin(angle), z);
        }
    }
}
