package faygolover.zoneartifacts.client.tesla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.tesla.Comet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
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
 * The Comet's explosion (the Comet itself is already gone): a flash, a fireball swelling to the
 * blast radius and fading from yellow to dark red, tongues of flame shooting out (away from the
 * wall it hit, stopping at blocks) and a spray of flames, sparks, lava drops and smoke.
 * Everything scales with the Comet's size; the amount of fire with its intensity.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CometEffectRenderer {

    private static final int LIFE_TICKS = 14;
    private static final int STANDARD_INTENSITY = 3;

    private static final int YELLOW = FireDraw.argb(255, 255, 225, 110);
    private static final int ORANGE = FireDraw.argb(255, 255, 120, 25);
    private static final int DEEP_RED = FireDraw.argb(255, 170, 30, 8);
    private static final int SOUL_CYAN = FireDraw.argb(255, 170, 245, 255);
    private static final int SOUL_BLUE = FireDraw.argb(255, 50, 160, 235);
    private static final int SOUL_DEEP = FireDraw.argb(255, 20, 45, 150);

    private record Tongue(Vec3 dir, Vec3 bend, double length) {
    }

    private static final class Blast {
        boolean cold;
        Vec3 center;
        double radius;
        float width;
        long startTick;
        final List<Tongue> tongues = new ArrayList<>();
    }

    private static final List<Blast> BLASTS = new ArrayList<>();

    private CometEffectRenderer() {
    }

    public static void onBurst(Vec3 center, @Nullable Vec3 normal, float size, int intensity, boolean cold) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        RandomSource rand = RandomSource.create();
        double factor = ModClientConfig.effective(intensity) / (double) STANDARD_INTENSITY;
        double radius = Comet.BLAST_RADIUS * size;

        Blast blast = new Blast();
        blast.center = center;
        blast.radius = radius;
        blast.width = 0.12f * (float) Math.sqrt(size);
        blast.startTick = level.getGameTime();
        blast.cold = cold;

        int count = Mth.clamp((int) Math.round(9 * factor), 3, 30);
        for (int i = 0; i < count; i++) {
            Vec3 dir = randomUnit(rand);
            if (normal != null) {
                dir = dir.add(normal.scale(0.9));
                if (dir.dot(normal) < 0.1) dir = dir.add(normal.scale(0.3 - dir.dot(normal)));
                dir = dir.normalize();
            }
            double length = radius * (0.45 + rand.nextDouble() * 0.45);
            BlockHitResult hit = level.clip(new ClipContext(center, center.add(dir.scale(length)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) length = Math.max(0.3, hit.getLocation().distanceTo(center));
            Vec3 side = dir.cross(randomUnit(rand));
            Vec3 bend = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize().scale(0.25).add(0, 0.2, 0);
            blast.tongues.add(new Tongue(dir, bend, length));
        }
        BLASTS.add(blast);

        // Particles, all at once.
        level.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0.0, 0.0, 0.0);
        int flames = (int) (24 * factor * Math.sqrt(size));
        for (int i = 0; i < flames; i++) {
            Vec3 v = outward(rand, normal).scale(0.08 + rand.nextDouble() * 0.25 * Math.sqrt(size));
            level.addParticle(cold ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, center.x, center.y, center.z, v.x, v.y, v.z);
        }
        int sparks = (int) (30 * factor * Math.sqrt(size));
        for (int i = 0; i < sparks; i++) {
            Vec3 v = outward(rand, normal).scale(0.05 + rand.nextDouble() * 0.12);
            if (cold) {
                level.addParticle(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z, v.x, v.y + 0.02, v.z);
            } else {
                level.addParticle(ModParticles.EMBER.get(), center.x, center.y, center.z, v.x, v.y + 0.02, v.z);
            }
        }
        for (int i = 0; i < 4 + (int) (3 * factor); i++) {
            if (cold) {
                Vec3 v = outward(rand, normal).scale(0.02).add(0, 0.03, 0);
                level.addParticle(ParticleTypes.SOUL, center.x, center.y, center.z, v.x, v.y, v.z);
            } else {
                level.addParticle(ParticleTypes.LAVA, center.x, center.y, center.z, 0.0, 0.0, 0.0);
            }
        }
        for (int i = 0; i < 6 + (int) (4 * factor); i++) {
            Vec3 p = center.add(randomUnit(rand).scale(radius * 0.3 * rand.nextDouble()));
            Vec3 v = outward(rand, normal).scale(0.03).add(0, 0.03, 0);
            if (cold) {
                level.addParticle(ModParticles.FROST_MIST.get(), p.x, p.y, p.z, v.x, v.y, v.z);
            } else {
                level.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, v.x, v.y, v.z);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BLASTS.clear();
            return;
        }
        if (BLASTS.isEmpty()) return;

        long now = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        // Flames and fireball all in the glow type (additive, no depth writes, see GlowRenderType).
        VertexConsumer buffer = bufferSource.getBuffer(GlowRenderType.GLOW);

        for (Iterator<Blast> it = BLASTS.iterator(); it.hasNext(); ) {
            Blast blast = it.next();
            float life = (now - blast.startTick + partialTick) / LIFE_TICKS;
            if (life >= 1.0f) {
                it.remove();
                continue;
            }
            float extend = 1.0f - (1.0f - Math.min(1.0f, life * 2.5f)) * (1.0f - Math.min(1.0f, life * 2.5f));
            float alpha = 1.0f - life * life;
            int bright = blast.cold ? SOUL_CYAN : YELLOW;
            int deep = blast.cold ? SOUL_DEEP : DEEP_RED;
            int body = FireDraw.mix(bright, FireDraw.mix(blast.cold ? SOUL_BLUE : ORANGE, deep, Math.max(0.0f, life * 2.0f - 1.0f)), Math.min(1.0f, life * 2.0f));

            for (Tongue tongue : blast.tongues) {
                Vec3[] points = FireDraw.tongue(blast.center, tongue.dir(), tongue.bend(), tongue.length() * extend, 6);
                float[] widths = new float[points.length];
                int[] colors = new int[points.length];
                for (int k = 0; k < points.length; k++) {
                    float s = k / (float) (points.length - 1);
                    widths[k] = blast.width * (1.0f - 0.8f * s) * (1.0f - 0.5f * life);
                    colors[k] = FireDraw.fade(FireDraw.mix(body, deep, s), alpha * (1.0f - 0.5f * s));
                }
                FireDraw.ribbon(matrix, buffer, points, widths, colors, cam);
            }

            // The fireball: drawn after all the flames, without depth writes (see GlowRenderType).
            double r = blast.radius * (0.25 + 0.45 * extend);
            FireDraw.glow(matrix, buffer, blast.center, r, cam, FireDraw.fade(body, 0.8f * alpha), 24);
            FireDraw.glow(matrix, buffer, blast.center, r * 0.45, cam, FireDraw.fade(bright, alpha * (1.0f - life)), 18);
        }

        bufferSource.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    /** Random direction, pushed away from the struck wall when there is one. */
    private static Vec3 outward(RandomSource rand, @Nullable Vec3 normal) {
        Vec3 dir = randomUnit(rand);
        if (normal != null && dir.dot(normal) < 0) dir = dir.subtract(normal.scale(2.0 * dir.dot(normal)));
        return dir;
    }

    private static Vec3 randomUnit(RandomSource rand) {
        double z = rand.nextDouble() * 2.0 - 1.0;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(1.0 - z * z);
        return new Vec3(r * Math.cos(angle), r * Math.sin(angle), z);
    }
}
