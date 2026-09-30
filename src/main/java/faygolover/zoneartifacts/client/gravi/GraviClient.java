package faygolover.zoneartifacts.client.gravi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.client.distortion.Distortion;
import faygolover.zoneartifacts.client.tesla.FireDraw;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.tesla.Gravi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gravi's pops, as each client sees them: a tiny Voronka. For {@link Gravi#WINDUP_TICKS} ticks the
 * air pinches in (a small lens, {@link Distortion}) and dust and chips of the surface are drawn in;
 * then a snap — a small ring of bent air, a pale ring, dust and chips flung out.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GraviClient {

    private static final int AFTER_TICKS = 10;
    private static final RandomSource RANDOM = RandomSource.create();

    private record Pop(Vec3 pos, @Nullable Vec3 normal, @Nullable BlockState surface, long start) {
        long due() {
            return start + Gravi.WINDUP_TICKS;
        }
    }

    private static final List<Pop> POPS = new ArrayList<>();
    private static ClientLevel lastLevel;

    private GraviClient() {
    }

    public static void onPop(Vec3 pos, @Nullable Vec3 normal, int stateId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (pos.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 64.0 * 64.0) return;
        BlockState state = stateId == 0 ? null : Block.stateById(stateId);
        if (state != null && state.isAir()) state = null;
        if (POPS.size() > 200) POPS.remove(0);
        POPS.add(new Pop(pos, normal, state, mc.level.getGameTime()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            POPS.clear();
            lastLevel = level;
        }
        if (level == null || mc.isPaused()) return;
        long now = level.getGameTime();
        for (Iterator<Pop> it = POPS.iterator(); it.hasNext(); ) {
            Pop pop = it.next();
            if (now > pop.due() + AFTER_TICKS) {
                it.remove();
                continue;
            }
            Vec3 p = pop.pos();
            if (now < pop.due()) {
                // Drawn in: dust from all round, chips of the surface.
                for (int i = 0; i < 2; i++) {
                    Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                    if (pop.normal() != null && dir.dot(pop.normal()) < 0) dir = dir.scale(-1);
                    Vec3 from = p.add(dir.scale(0.7 + RANDOM.nextDouble() * 0.3));
                    Vec3 v = p.subtract(from).scale(1.0 / 7.0);
                    faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, ModParticles.GRAV_DUST.get(), from.x, from.y, from.z, v.x, v.y, v.z);
                }
                if (pop.surface() != null && RANDOM.nextInt(2) == 0) {
                    Vec3 from = p.subtract(pop.normal() != null ? pop.normal().scale(0.25) : Vec3.ZERO);
                    Vec3 v = p.subtract(from).scale(0.2).add(RANDOM.nextGaussian() * 0.02, 0.0, RANDOM.nextGaussian() * 0.02);
                    faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, pop.surface()), from.x, from.y, from.z, v.x, v.y, v.z);
                }
            } else if (now == pop.due()) {
                // The snap.
                for (int i = 0; i < 10; i++) {
                    Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize()
                            .scale(0.12 + RANDOM.nextDouble() * 0.15);
                    if (pop.normal() != null && v.dot(pop.normal()) < 0) v = v.subtract(pop.normal().scale(2.0 * v.dot(pop.normal())));
                    faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, ModParticles.GRAV_DUST.get(), p.x, p.y, p.z, v.x, v.y, v.z);
                }
                if (pop.surface() != null) {
                    for (int i = 0; i < 6; i++) {
                        Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.12, 0.12 + RANDOM.nextDouble() * 0.12, RANDOM.nextGaussian() * 0.12);
                        faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, pop.surface()), p.x, p.y, p.z, v.x, v.y, v.z);
                    }
                }
            }
        }
    }

    /** The bent air, for {@code Distortion}: a small pinch while sucking in, a ring rushing out after. */
    public static void collect(List<Distortion.Patch> out, long now, float partial) {
        float time = (now % 72000L) + partial;
        for (Pop pop : POPS) {
            float t = (now - pop.start() + partial) / Gravi.WINDUP_TICKS;
            if (t < 1.0f) {
                out.add(new Distortion.Lens(pop.pos(), 0.9, 0.3 + 1.2 * t, 0.4 * t, 0.02, time * 0.1, 0.0, 0.8, 0.1,
                        0.0, Math.min(1.0f, t * 3.0f), 10, 20));
            } else {
                float after = (now - pop.due() + partial) / AFTER_TICKS;
                if (after >= 1.0f) continue;
                float e = 1.0f - (1.0f - after) * (1.0f - after);
                out.add(new Distortion.Lens(pop.pos(), 0.4 + 1.1 * e, 0.0, 0.0, 0.0, 0.0, -0.1 * (1.0f - after), 0.75, 0.12,
                        0.0, 1.0f - after * after, 10, 20));
            }
        }
    }

    /** A small pale ring at each snap. */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || POPS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(GlowRenderType.GLOW);
        for (Pop pop : POPS) {
            float after = (now - pop.due() + partial) / AFTER_TICKS;
            if (after < 0.0f || after >= 1.0f) continue;
            float e = 1.0f - (1.0f - after) * (1.0f - after);
            ring(matrix, buffer, pop.pos(), 0.15 + 1.0 * e, cam, 0.02f + 0.03f * (1.0f - after),
                    FireDraw.argb((int) (150 * (1.0f - after)), 225, 225, 235));
        }
        bufferSource.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    private static void ring(Matrix4f matrix, VertexConsumer buffer, Vec3 c, double radius, Vec3 cam, float halfWidth, int color) {
        Vec3 toCam = cam.subtract(c);
        if (toCam.lengthSqr() < 1.0E-6) return;
        toCam = toCam.normalize();
        Vec3 up = Math.abs(toCam.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = toCam.cross(up).normalize();
        Vec3 upOnPlane = right.cross(toCam).normalize();
        int n = 24;
        Vec3[] points = new Vec3[n + 1];
        float[] widths = new float[n + 1];
        int[] colors = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2.0 * i / n;
            points[i] = c.add(right.scale(Math.cos(a) * radius)).add(upOnPlane.scale(Math.sin(a) * radius));
            widths[i] = halfWidth;
            colors[i] = color;
        }
        FireDraw.ribbon(matrix, buffer, points, widths, colors, cam);
    }
}
