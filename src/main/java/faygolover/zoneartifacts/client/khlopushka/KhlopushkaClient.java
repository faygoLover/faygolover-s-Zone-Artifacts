package faygolover.zoneartifacts.client.khlopushka;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.client.distortion.Distortion;
import faygolover.zoneartifacts.client.fx.HumLoop;
import faygolover.zoneartifacts.client.tesla.LightningDraw;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Khlopushka's look: the clot — a small white-hot spark growing into a trembling ball of light,
 * spitting sparks faster and faster — and its blast: a flash of light swelling out, sparks, smoke,
 * a ripple through the air. For whoever it blinded: the screen goes white and slowly clears, the
 * ears ring and the world goes quiet ({@code ScreenFx}, {@code SoundFx}).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KhlopushkaClient {

    private static final RandomSource RANDOM = RandomSource.create();
    private static final int BLAST_TICKS = 8;

    private record Clot(Vec3 at, long start, int ticks) {
    }

    private record Blast(Vec3 at, float radius, long born) {
    }

    private static final List<Clot> CLOTS = new ArrayList<>();
    private static final List<Blast> BLASTS = new ArrayList<>();
    private static float flashStrength;
    private static int flashTicks;
    private static long flashStart = -1;
    @Nullable
    private static HumLoop ringing;

    private KhlopushkaClient() {
    }

    public static void onPacket(int type, Vec3 at, float value, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = level.getGameTime();
        switch (type) {
            case 0 -> CLOTS.add(new Clot(at, now, Math.max(1, ticks)));
            case 1 -> {
                CLOTS.removeIf(c -> c.at().distanceToSqr(at) < 0.25);
                BLASTS.add(new Blast(at, value, now));
                level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0.0, 0.0, 0.0);
                for (int i = 0; i < 40; i++) {
                    Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(0.2 + RANDOM.nextDouble() * 0.4);
                    level.addParticle(ParticleTypes.FIREWORK, at.x, at.y, at.z, v.x, v.y, v.z);
                }
                for (int i = 0; i < 12; i++) {
                    Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() + 0.5, RANDOM.nextGaussian()).scale(0.04);
                    level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, v.x, v.y, v.z);
                }
            }
            default -> {
                // Blinded: the stronger flash wins.
                float current = flash(0.0f);
                if (value >= current) {
                    flashStrength = value;
                    flashTicks = Math.max(10, ticks);
                    flashStart = now;
                }
                if (ringing == null || ringing.isStopped()) {
                    ringing = new HumLoop(ModSounds.KHLOPUSHKA_RING.get(), () -> 0.8 * flash(0.0f));
                    mc.getSoundManager().play(ringing);
                }
            }
        }
    }

    /** 0..1: how white the own screen is from a flash. */
    public static float flash(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || flashStart < 0) return 0.0f;
        float t = (mc.level.getGameTime() - flashStart) + partial;
        if (t < 0) return 0.0f;
        if (t < 6) return flashStrength;
        float k = 1.0f - (t - 6) / Math.max(1.0f, flashTicks - 6);
        return k <= 0.0f ? 0.0f : flashStrength * (float) Math.pow(k, 0.6);
    }

    /** Deafened by the bang. */
    public static float hearing() {
        return 1.0f - 0.85f * flash(0.0f);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            CLOTS.clear();
            BLASTS.clear();
            flashStart = -1;
            return;
        }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        CLOTS.removeIf(c -> now - c.start() > c.ticks() + 20);
        BLASTS.removeIf(b -> now - b.born() > BLAST_TICKS);
        for (Clot c : CLOTS) {
            float t = Mth.clamp((now - c.start()) / (float) c.ticks(), 0.0f, 1.0f);
            if (RANDOM.nextFloat() < 0.2f + 0.8f * t) {
                Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).scale(0.08 + 0.1 * t);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.at().x, c.at().y, c.at().z, v.x, v.y, v.z);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (CLOTS.isEmpty() && BLASTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        float partial = event.getPartialTick();
        float time = (now % 72000L) + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(GlowRenderType.GLOW);
        for (Clot c : CLOTS) {
            float t = Mth.clamp((now - c.start() + partial) / c.ticks(), 0.0f, 1.0f);
            float flicker = 1.0f + (0.1f + 0.35f * t) * Mth.sin(time * (1.3f + 2.0f * t)) * Mth.sin(time * 0.7f + 1.0f);
            double core = (0.06 + 0.22 * t) * flicker;
            LightningDraw.glow(m, vc, c.at(), (0.35 + 0.9 * t) * flicker, cam, 120, 170, 255, (int) (60 + 90 * t), 18);
            LightningDraw.glow(m, vc, c.at(), core * 2.2, cam, 255, 240, 200, (int) (140 + 80 * t), 16);
            LightningDraw.glow(m, vc, c.at(), core, cam, 255, 255, 255, 250, 14);
        }
        for (Blast b : BLASTS) {
            float t = Mth.clamp((now - b.born() + partial) / BLAST_TICKS, 0.0f, 1.0f);
            float a = (1.0f - t) * (1.0f - t);
            LightningDraw.glow(m, vc, b.at(), b.radius() * (0.4 + 1.0 * t), cam, 255, 250, 230, (int) (230 * a), 24);
            LightningDraw.glow(m, vc, b.at(), b.radius() * (0.2 + 0.4 * t), cam, 255, 255, 255, (int) (255 * a), 18);
        }
        buffers.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    public static void collect(List<Distortion.Patch> out, long now, float partial) {
        float time = (now % 72000L) + partial;
        for (Clot c : CLOTS) {
            float t = Mth.clamp((now - c.start() + partial) / c.ticks(), 0.0f, 1.0f);
            out.add(Distortion.Lens.shimmer(c.at(), 0.4 + 0.6 * t, 0.03 + 0.05 * t, time * 0.2, 0.9f));
        }
        for (Blast b : BLASTS) {
            float t = Mth.clamp((now - b.born() + partial) / BLAST_TICKS, 0.0f, 1.0f);
            out.add(Distortion.Lens.shimmer(b.at(), b.radius() * (0.5 + 1.2 * t), 0.1 * (1.0f - t), time * 0.4, 1.0f - t));
        }
    }
}
