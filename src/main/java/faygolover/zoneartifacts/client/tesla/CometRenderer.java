package faygolover.zoneartifacts.client.tesla;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.tesla.CometEntity;
import faygolover.zoneartifacts.tesla.Tesla;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Comet: a blazing point at the center that slowly shifts between orange and yellow, wrapped
 * in a halo (the Cold Comet: the same in soul-fire blue and cyan), with
 * <ul>
 *     <li><b>prominences</b> — arches of flame that rise off the surface and sink back (as many as
 *     the intensity, capped by the player's {@code maxEffectIntensity}), slowly turning with the
 *     ball, plus a faint ring of fire circling it;</li>
 *     <li><b>flares</b> — now and then a short tongue of flame thrown out to the side, where the
 *     Tesla has its grabbing arcs.</li>
 * </ul>
 * While spawning it grows from a point, like the Tesla. Sparks and smoke come from
 * {@link CometEntity}'s client tick.
 */
public class CometRenderer extends EntityRenderer<CometEntity> {

    private static final ResourceLocation UNUSED_TEXTURE = new ResourceLocation(ZoneArtifacts.MODID, "textures/entity/comet.png");

    /** Surface radius at size 1. */
    private static final double RADIUS = 0.36;

    private static final int ORANGE = FireDraw.argb(255, 255, 110, 20);
    private static final int YELLOW = FireDraw.argb(255, 255, 210, 60);
    private static final int DEEP_RED = FireDraw.argb(255, 220, 45, 10);
    private static final int HOT_WHITE = FireDraw.argb(245, 255, 246, 205);

    // The Cold Comet's soul fire: the same roles, in blue and cyan.
    private static final int SOUL_BLUE = FireDraw.argb(255, 40, 150, 230);
    private static final int SOUL_CYAN = FireDraw.argb(255, 110, 240, 250);
    private static final int SOUL_DEEP = FireDraw.argb(255, 25, 60, 170);
    private static final int SOUL_WHITE = FireDraw.argb(245, 230, 255, 255);

    private static final int MIN_PROMINENCE_LIFE = 25;
    private static final int MAX_PROMINENCE_LIFE = 60;
    private static final int ARCH_SEGMENTS = 10;

    private static final int MIN_FLARE_PAUSE = 15;
    private static final int MAX_FLARE_PAUSE = 45;
    private static final int MIN_FLARE_LIFE = 6;
    private static final int MAX_FLARE_LIFE = 11;

    private static final Map<CometEntity, Ball> BALLS = new WeakHashMap<>();

    public CometRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(CometEntity entity) {
        return UNUSED_TEXTURE;
    }

    /**
     * Only notes the Comet down: it's drawn later, after every entity and the clouds
     * ({@link #renderPending}). Its fire doesn't write depth (overlapping flames would fight), so
     * anything drawn after it — mobs, clouds — used to show through it.
     */
    @Override
    public void render(CometEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        if (entity.getState().isVisible()) PENDING.add(entity);
    }

    private static final Set<CometEntity> PENDING = new LinkedHashSet<>();

    private static float grow(CometEntity entity, float partialTick) {
        if (entity.getState() != TeslaEntity.State.SPAWNING) return 1.0f;
        float t = Mth.clamp(entity.clientStateAge(partialTick) / Tesla.SPAWN_GROW_TICKS, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** Draws the Comets noted this frame; called at {@code AFTER_WEATHER} (after the clouds). */
    static void renderPending(RenderLevelStageEvent event) {
        if (PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float partialTick = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        // After the clouds vanilla has already put the camera's rotation into the model-view
        // matrix; the event's pose has it too. Undo the first, so it isn't applied twice (the ball
        // would drift off its place as the camera turns): MV x pose = the event's pose.
        Matrix4f base = new Matrix4f(RenderSystem.getModelViewMatrix()).invert().mul(event.getPoseStack().last().pose());
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(base);

        // Pass 1: the core, solid (plain alpha blending) — nothing behind it shows through.
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f world = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        BufferBuilder core = Tesselator.getInstance().getBuilder();
        core.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (CometEntity entity : PENDING) {
            if (entity.isRemoved()) continue;
            float grow = grow(entity, partialTick);
            if (grow < 0.02f) continue;
            Vec3 c = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() / 2.0, 0.0);
            boolean cold = entity.isCold();
            int color = FireDraw.mix(cold ? SOUL_CYAN : YELLOW, cold ? SOUL_WHITE : HOT_WHITE, 0.5f);
            coreDisc(core, world, c, 0.15 * grow * entity.getSize(), cam, color);
        }
        BufferUploader.drawWithShader(core.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();

        // Pass 2: the fire (additive glow).
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        for (CometEntity entity : PENDING) {
            if (entity.isRemoved()) continue;
            Vec3 pos = entity.getPosition(partialTick);
            poseStack.pushPose();
            poseStack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            drawFire(entity, partialTick, poseStack, bufferSource);
            poseStack.popPose();
        }
        bufferSource.endBatch(GlowRenderType.GLOW);
        RenderSystem.depthMask(true);
        PENDING.clear();
    }

    /** A camera-facing disc, solid in the middle and soft at the rim. */
    private static void coreDisc(BufferBuilder buffer, Matrix4f m, Vec3 c, double radius, Vec3 cam, int color) {
        Vec3 toCam = cam.subtract(c);
        if (toCam.lengthSqr() < 1.0E-6 || radius < 0.01) return;
        toCam = toCam.normalize();
        Vec3 up = Math.abs(toCam.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = toCam.cross(up).normalize();
        Vec3 upOnPlane = right.cross(toCam).normalize();
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int slices = 20;
        double solid = radius * 0.7;
        for (int i = 0; i < slices; i++) {
            double a0 = Math.PI * 2.0 * i / slices;
            double a1 = Math.PI * 2.0 * (i + 1) / slices;
            Vec3 d0 = right.scale(Math.cos(a0)).add(upOnPlane.scale(Math.sin(a0)));
            Vec3 d1 = right.scale(Math.cos(a1)).add(upOnPlane.scale(Math.sin(a1)));
            // Solid fan...
            put(buffer, m, c, r, g, b, 245);
            put(buffer, m, c.add(d0.scale(solid)), r, g, b, 245);
            put(buffer, m, c.add(d1.scale(solid)), r, g, b, 245);
            put(buffer, m, c.add(d1.scale(solid)), r, g, b, 245);
            // ...and a soft rim.
            put(buffer, m, c.add(d0.scale(solid)), r, g, b, 245);
            put(buffer, m, c.add(d0.scale(radius)), r, g, b, 0);
            put(buffer, m, c.add(d1.scale(radius)), r, g, b, 0);
            put(buffer, m, c.add(d1.scale(solid)), r, g, b, 245);
        }
    }

    private static void put(BufferBuilder buffer, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        buffer.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    private static void drawFire(CometEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        TeslaEntity.State state = entity.getState();
        float grow = grow(entity, partialTick);
        if (grow < 0.02f) return;

        boolean cold = entity.isCold();
        int cBase = cold ? SOUL_BLUE : ORANGE;
        int cBright = cold ? SOUL_CYAN : YELLOW;
        int cDeep = cold ? SOUL_DEEP : DEEP_RED;
        int cHot = cold ? SOUL_WHITE : HOT_WHITE;

        float size = entity.getSize();
        double scale = grow * size;
        double radius = RADIUS * scale;
        float widthScale = (float) Math.sqrt(size) * grow;
        int intensity = ModClientConfig.effective(entity.getIntensity());

        Vec3 lerpPos = entity.getPosition(partialTick);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(lerpPos);
        Vec3 center = new Vec3(0.0, entity.getBbHeight() / 2.0, 0.0);
        Matrix4f matrix = poseStack.last().pose();
        // Everything fiery in the glow type (additive, no depth writes): overlapping ribbons and the
        // halo never fight over depth, and dim ribbons in front no longer cut dark gaps into the glow.
        VertexConsumer buffer = buffers.getBuffer(GlowRenderType.GLOW);

        long now = entity.level().getGameTime();
        float time = (now % 72000L) + partialTick;
        Ball ball = BALLS.computeIfAbsent(entity, e -> new Ball(e.getId()));
        ball.update(now, intensity);

        // Colour of the moment: orange drifting to yellow and back.
        float shift = 0.5f + 0.5f * Mth.sin(time * 0.05f + ball.phase);
        int body = FireDraw.mix(cBase, cBright, shift);
        float spin = time * ball.spinSpeed;

        // ---- prominences: arches rising from the surface and sinking back ----
        for (Prominence p : ball.prominences) {
            float t = Mth.clamp((now - p.start + partialTick) / (float) (p.expires - p.start), 0.0f, 1.0f);
            float rise = Mth.sin((float) Math.PI * t);
            float fade = Math.min(1.0f, t * 5.0f) * Math.min(1.0f, (1.0f - t) * 5.0f);
            if (fade <= 0.01f) continue;
            Vec3 a = rotate(p.from, ball.axis, spin);
            Vec3 b = rotate(p.to, ball.axis, spin);

            Vec3[] points = new Vec3[ARCH_SEGMENTS + 1];
            float[] outer = new float[ARCH_SEGMENTS + 1];
            float[] inner = new float[ARCH_SEGMENTS + 1];
            int[] outerColor = new int[ARCH_SEGMENTS + 1];
            int[] innerColor = new int[ARCH_SEGMENTS + 1];
            for (int k = 0; k <= ARCH_SEGMENTS; k++) {
                double s = k / (double) ARCH_SEGMENTS;
                double bump = Math.sin(Math.PI * s);
                Vec3 dir = a.scale(1.0 - s).add(b.scale(s)).normalize();
                double wobble = 0.04 * Math.sin(time * 0.25 + k * 1.3 + p.seed);
                points[k] = center.add(dir.scale(radius * (0.92 + (p.height * rise + wobble) * bump)));
                outer[k] = 0.045f * widthScale * (0.5f + 0.5f * (float) bump);
                inner[k] = 0.018f * widthScale * (0.5f + 0.5f * (float) bump);
                outerColor[k] = FireDraw.fade(FireDraw.mix(cDeep, body, (float) bump * 0.6f), 0.6f * fade);
                innerColor[k] = FireDraw.fade(FireDraw.mix(body, cHot, (float) bump * 0.5f), 0.9f * fade);
            }
            FireDraw.ribbon(matrix, buffer, points, outer, outerColor, cam);
            FireDraw.ribbon(matrix, buffer, points, inner, innerColor, cam);
        }

        // ---- a faint ring of fire circling the ball ----
        int ringPoints = 28;
        Vec3[] ring = new Vec3[ringPoints + 1];
        float[] ringWidth = new float[ringPoints + 1];
        int[] ringColor = new int[ringPoints + 1];
        Vec3 u = ball.ringU;
        Vec3 v = ball.ringV;
        for (int k = 0; k <= ringPoints; k++) {
            double angle = Math.PI * 2.0 * k / ringPoints + time * 0.04;
            double r = radius * (1.25 + 0.05 * Math.sin(angle * 3.0 + time * 0.2));
            ring[k] = center.add(rotate(u.scale(Math.cos(angle) * r).add(v.scale(Math.sin(angle) * r)), ball.axis, spin * 0.5f));
            float flicker = 0.55f + 0.45f * Mth.sin((float) (angle * 2.0) + time * 0.3f);
            ringWidth[k] = 0.02f * widthScale;
            ringColor[k] = FireDraw.fade(FireDraw.mix(cDeep, body, flicker), 0.45f * flicker);
        }
        FireDraw.ribbon(matrix, buffer, ring, ringWidth, ringColor, cam);

        // ---- flares: short tongues of flame thrown out to the side ----
        if (state != TeslaEntity.State.SPAWNING) {
            for (Flare f : ball.flares) {
                if (f == null || now < f.start) continue;
                float t = Mth.clamp((now - f.start + partialTick) / (float) (f.expires - f.start), 0.0f, 1.0f);
                float extend = 1.0f - (1.0f - t) * (1.0f - t);
                float alpha = 1.0f - t * t;
                Vec3 dir = rotate(f.dir, ball.axis, spin);
                Vec3 bend = rotate(f.bend, ball.axis, spin);
                Vec3 base = center.add(dir.scale(radius * 0.9));
                Vec3[] points = FireDraw.tongue(base, dir, bend, f.length * scale * extend, 6);
                float[] widths = new float[points.length];
                int[] colors = new int[points.length];
                for (int k = 0; k < points.length; k++) {
                    float s = k / (float) (points.length - 1);
                    widths[k] = 0.06f * widthScale * (1.0f - s) * (1.0f - 0.3f * t);
                    colors[k] = FireDraw.fade(FireDraw.mix(FireDraw.mix(body, cHot, 0.4f), cDeep, s), alpha * (1.0f - 0.6f * s));
                }
                FireDraw.ribbon(matrix, buffer, points, widths, colors, cam);
            }
        }

        // ---- the glowing core: its own pass without depth writes (additive, so the order doesn't
        // matter). Its layers used to fight over the same depth (the rippling halo), and the
        // halo's disc hid the arches behind the ball.
        float pulse = 1.0f + 0.08f * Mth.sin(time * 0.7f + ball.phase);
        // (Kept soft: a bright, wide middle glow made the whole ball look much bigger than it is.)
        FireDraw.glow(matrix, buffer, center, 0.7 * scale * pulse, cam, FireDraw.fade(FireDraw.mix(cDeep, body, 0.5f), 0.28f), 20);
        // Like the Tesla's: past the pulsing halo only a faint tint of the body's colour and the small
        // white-hot heart (the old bright middle glow and wide opaque disc made the core look huge).
        FireDraw.glow(matrix, buffer, center, 0.36 * scale * pulse, cam, FireDraw.fade(body, 0.16f), 18);
        Vec3 toCam = cam.subtract(center).normalize();
        FireDraw.glow(matrix, buffer, center.add(toCam.scale(0.02 * size)), 0.17 * scale * pulse, cam,
                FireDraw.mix(cHot, cBright, 0.3f * shift), 14);
    }

    /** Rotates {@code v} around the unit {@code axis} by {@code angle} radians (Rodrigues). */
    private static Vec3 rotate(Vec3 v, Vec3 axis, float angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return v.scale(cos).add(axis.cross(v).scale(sin)).add(axis.scale(axis.dot(v) * (1.0 - cos)));
    }

    private static Vec3 randomUnit(RandomSource random) {
        double z = random.nextDouble() * 2.0 - 1.0;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(1.0 - z * z);
        return new Vec3(r * Math.cos(angle), r * Math.sin(angle), z);
    }

    /** Per-Comet client state. */
    private static final class Ball {
        final RandomSource random = RandomSource.create();
        final List<Prominence> prominences = new ArrayList<>();
        Flare[] flares = new Flare[0];
        final float phase;
        final float spinSpeed;
        final Vec3 axis;
        final Vec3 ringU;
        final Vec3 ringV;
        long lastUpdate = -1_000_000L;

        Ball(int id) {
            this.phase = (id * 0.618f) % 1.0f * (float) (Math.PI * 2.0);
            this.spinSpeed = 0.015f + random.nextFloat() * 0.015f;
            this.axis = randomUnit(random);
            Vec3 helper = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 tilt = randomUnit(random).scale(0.5).add(axis).normalize();
            Vec3 u = tilt.cross(helper).normalize();
            this.ringU = u;
            this.ringV = tilt.cross(u).normalize();
        }

        void update(long now, int intensity) {
            if (now == lastUpdate) return;
            lastUpdate = now;

            while (prominences.size() < intensity) {
                Prominence p = Prominence.create(random, now);
                // Stagger so they don't all rise and sink together.
                long life = p.expires - p.start;
                long age = random.nextInt((int) life);
                p.start -= age;
                p.expires -= age;
                prominences.add(p);
            }
            while (prominences.size() > intensity) prominences.remove(prominences.size() - 1);
            for (int i = 0; i < prominences.size(); i++) {
                if (now >= prominences.get(i).expires) prominences.set(i, Prominence.create(random, now));
            }

            int flareCount = Math.max(1, (intensity + 1) / 2);
            if (flares.length != flareCount) {
                flares = new Flare[flareCount];
                for (int i = 0; i < flareCount; i++) flares[i] = Flare.create(random, now + random.nextInt(MAX_FLARE_PAUSE));
            }
            for (int i = 0; i < flares.length; i++) {
                if (now >= flares[i].expires) {
                    flares[i] = Flare.create(random, now + MIN_FLARE_PAUSE + random.nextInt(MAX_FLARE_PAUSE - MIN_FLARE_PAUSE + 1));
                }
            }
        }
    }

    /** An arch between two surface points (unit directions, before the ball's spin). */
    private static final class Prominence {
        Vec3 from;
        Vec3 to;
        double height;
        float seed;
        long start;
        long expires;

        static Prominence create(RandomSource random, long now) {
            Prominence p = new Prominence();
            p.from = randomUnit(random);
            // The other foot 25–60° away.
            Vec3 helper = randomUnit(random);
            Vec3 perp = p.from.cross(helper);
            if (perp.lengthSqr() < 1.0E-4) perp = p.from.cross(new Vec3(0, 1, 0.3));
            perp = perp.normalize();
            double angle = Math.toRadians(25 + random.nextDouble() * 35);
            p.to = p.from.scale(Math.cos(angle)).add(perp.scale(Math.sin(angle))).normalize();
            p.height = 0.35 + random.nextDouble() * 0.45;
            p.seed = random.nextFloat() * 10.0f;
            p.start = now;
            p.expires = now + MIN_PROMINENCE_LIFE + random.nextInt(MAX_PROMINENCE_LIFE - MIN_PROMINENCE_LIFE + 1);
            return p;
        }
    }

    /** A tongue of flame, starting at {@code start}. */
    private static final class Flare {
        Vec3 dir;
        Vec3 bend;
        double length;
        long start;
        long expires;

        static Flare create(RandomSource random, long start) {
            Flare f = new Flare();
            f.dir = randomUnit(random);
            Vec3 side = f.dir.cross(randomUnit(random));
            f.bend = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize().scale(0.4 + random.nextDouble() * 0.4);
            f.length = 0.35 + random.nextDouble() * 0.45;
            f.start = start;
            f.expires = start + MIN_FLARE_LIFE + random.nextInt(MAX_FLARE_LIFE - MIN_FLARE_LIFE + 1);
            return f;
        }
    }
}
