package faygolover.zoneartifacts.client.thermal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.config.ModClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Iney's frost: small glowing six-pointed crystals lying flat on the block faces inside the zone.
 * Always faintly visible; brighter while the anomaly is active, each one twinkling on its own.
 * Crystal positions are seeded from the face, so they stay put (no flicker when the face cache
 * refreshes). How many are drawn per face follows the effective intensity.
 * <p>
 * Drawn with {@link GlowRenderType#GLOW} (vanilla lightning look — additive, unlit, back faces
 * culled — without depth writes, so neighbouring crystals never flicker against each other): every
 * quad is wound counter-clockwise as seen from the face's outside.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IneyFrostRenderer {

    /** Crystals prepared per face; the first {@link #perFace(int)} of them are drawn. */
    private static final int MAX_PER_FACE = 7;
    private static final double LIFT = 0.004;
    private static final int R = 175, G = 228, B = 255;

    private record Crystal(Vec3 center, Vec3 normal, Vec3 t, Vec3 b, float size, float rotation, float phase) {
    }

    private record Cache(long version, List<List<Crystal>> perFace) {
    }

    private static final Map<ThermalClientHandler.Key, Cache> CACHES = new HashMap<>();

    private IneyFrostRenderer() {
    }

    private static int perFace(int effectiveIntensity) {
        return Mth.clamp(1 + (effectiveIntensity * 6) / 10, 1, MAX_PER_FACE);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CACHES.clear();
            return;
        }

        float partial = event.getPartialTick();
        float time = (mc.level.getGameTime() % 72000L) + partial;
        Vec3 cam = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = null;

        Map<ThermalClientHandler.Key, Cache> used = new HashMap<>();
        for (Map.Entry<ThermalClientHandler.Key, ThermalClientHandler.State> e : ThermalClientHandler.states()) {
            ThermalClientHandler.State state = e.getValue();
            if (e.getValue().entry() != null && !e.getValue().entry().visible()) continue;
            if (state.isZharka() || state.faces().isEmpty()) continue;

            Cache cache = CACHES.get(e.getKey());
            if (cache == null || cache.version() != state.facesVersion()) {
                cache = build(state);
            }
            used.put(e.getKey(), cache);

            float activity = state.activity(partial);
            int count = perFace(ModClientConfig.effective(state.entry().intensity()));
            float baseAlpha = 0.16f + 0.6f * activity;

            if (buffer == null) buffer = bufferSource.getBuffer(GlowRenderType.GLOW);
            for (List<Crystal> crystals : cache.perFace()) {
                for (int i = 0; i < count && i < crystals.size(); i++) {
                    Crystal c = crystals.get(i);
                    float twinkle = 0.65f + 0.35f * Mth.sin(time * (0.05f + 0.05f * activity) + c.phase());
                    int alpha = (int) (255 * Mth.clamp(baseAlpha * twinkle, 0.0f, 1.0f));
                    if (alpha <= 2) continue;
                    drawCrystal(matrix, buffer, c, alpha);
                }
            }
        }
        CACHES.clear();
        CACHES.putAll(used);

        if (buffer != null) bufferSource.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    private static Cache build(ThermalClientHandler.State state) {
        List<List<Crystal>> perFace = new ArrayList<>(state.faces().size());
        for (ThermalClientHandler.Face face : state.faces()) {
            Vec3 center = face.center();
            long seed = Double.doubleToLongBits(center.x * 31.0 + center.y * 1013.0 + center.z * 7919.0) ^ (face.direction().ordinal() * 0x9E3779B97F4A7C15L);
            RandomSource random = RandomSource.create(seed);
            Vec3 n = ThermalClientHandler.normal(face.direction());
            Vec3 t = ThermalClientHandler.tangent(face.direction());
            Vec3 b = n.cross(t);
            List<Crystal> crystals = new ArrayList<>(MAX_PER_FACE);
            for (int i = 0; i < MAX_PER_FACE; i++) {
                double u = (random.nextDouble() - 0.5) * 0.8;
                double v = (random.nextDouble() - 0.5) * 0.8;
                Vec3 p = ThermalClientHandler.pointOnFace(face, u, v, LIFT + i * 0.0004);
                float size = 0.04f + random.nextFloat() * 0.09f;
                crystals.add(new Crystal(p, n, t, b, size, random.nextFloat() * (float) Math.PI, random.nextFloat() * 6.2831855f));
            }
            perFace.add(crystals);
        }
        return new Cache(state.facesVersion(), perFace);
    }

    /** Three thin rhombi at 60° to each other (a six-pointed star) and a small bright core. */
    private static void drawCrystal(Matrix4f matrix, VertexConsumer buffer, Crystal c, int alpha) {
        for (int k = 0; k < 3; k++) {
            float angle = c.rotation() + k * (float) (Math.PI / 3.0);
            Vec3 d = c.t().scale(Mth.cos(angle)).add(c.b().scale(Mth.sin(angle)));
            Vec3 e = c.normal().cross(d);
            rhombus(matrix, buffer, c.center(), d.scale(c.size()), e.scale(c.size() * 0.14), alpha);
        }
        rhombus(matrix, buffer, c.center(), c.t().scale(c.size() * 0.3), c.b().scale(c.size() * 0.3), Math.min(255, alpha + 40));
    }

    /** Rhombus with half-diagonals {@code d} and {@code e}; CCW seen from {@code d × e}. */
    private static void rhombus(Matrix4f matrix, VertexConsumer buffer, Vec3 c, Vec3 d, Vec3 e, int alpha) {
        vertex(matrix, buffer, c.subtract(d), alpha);
        vertex(matrix, buffer, c.subtract(e), alpha);
        vertex(matrix, buffer, c.add(d), alpha);
        vertex(matrix, buffer, c.add(e), alpha);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer buffer, Vec3 p, int alpha) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(R, G, B, alpha).endVertex();
    }
}
