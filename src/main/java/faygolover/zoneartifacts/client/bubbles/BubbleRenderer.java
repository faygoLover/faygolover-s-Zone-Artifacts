package faygolover.zoneartifacts.client.bubbles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.BubbleEntity;
import faygolover.zoneartifacts.client.GlowRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * A soap bubble: a camera-facing film with a thin rainbow rim (its colours running round it), a
 * faint sheen across it and a bright little highlight up to one side, gently wobbling. Charging, it
 * trembles, swells and brightens.
 */
public class BubbleRenderer extends EntityRenderer<BubbleEntity> {

    private static final ResourceLocation UNUSED = new ResourceLocation(ZoneArtifacts.MODID, "textures/entity/comet.png");
    private static final int SEGMENTS = 36;

    public BubbleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(BubbleEntity entity) {
        return UNUSED;
    }

    @Override
    public void render(BubbleEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float time = entity.tickCount + partialTick + entity.getId() * 13.1f;
        float charge = entity.charging() ? 1.0f - Math.max(0, entity.charge()) / 5.0f : 0.0f;
        float r = 0.38f * (1.0f + 0.03f * Mth.sin(time * 0.21f) + 0.25f * charge + (charge > 0 ? 0.04f * Mth.sin(time * 3.1f) : 0.0f));
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() / 2.0, 0.0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(GlowRenderType.GLOW);
        float bright = 1.0f + 1.5f * charge;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float w0 = 1.0f + 0.03f * Mth.sin(a0 * 3.0f + time * 0.17f);
            float w1 = 1.0f + 0.03f * Mth.sin(a1 * 3.0f + time * 0.17f);
            int c0 = film(a0, time);
            int c1 = film(a1, time);
            // Faint sheen over the film.
            put(vc, m, 0.0f, 0.0f, c0, (int) (6 * bright));
            put(vc, m, Mth.cos(a0) * r * w0 * 0.85f, Mth.sin(a0) * r * w0 * 0.85f, c0, (int) (22 * bright));
            put(vc, m, Mth.cos(a1) * r * w1 * 0.85f, Mth.sin(a1) * r * w1 * 0.85f, c1, (int) (22 * bright));
            put(vc, m, 0.0f, 0.0f, c1, (int) (6 * bright));
            // The rim, brightest just inside the edge.
            put(vc, m, Mth.cos(a0) * r * w0 * 0.85f, Mth.sin(a0) * r * w0 * 0.85f, c0, (int) (22 * bright));
            put(vc, m, Mth.cos(a0) * r * w0, Mth.sin(a0) * r * w0, c0, (int) (Math.min(255, 110 * bright)));
            put(vc, m, Mth.cos(a1) * r * w1, Mth.sin(a1) * r * w1, c1, (int) (Math.min(255, 110 * bright)));
            put(vc, m, Mth.cos(a1) * r * w1 * 0.85f, Mth.sin(a1) * r * w1 * 0.85f, c1, (int) (22 * bright));
            put(vc, m, Mth.cos(a0) * r * w0, Mth.sin(a0) * r * w0, c0, (int) (Math.min(255, 110 * bright)));
            put(vc, m, Mth.cos(a0) * r * w0 * 1.05f, Mth.sin(a0) * r * w0 * 1.05f, c0, 0);
            put(vc, m, Mth.cos(a1) * r * w1 * 1.05f, Mth.sin(a1) * r * w1 * 1.05f, c1, 0);
            put(vc, m, Mth.cos(a1) * r * w1, Mth.sin(a1) * r * w1, c1, (int) (Math.min(255, 110 * bright)));
        }
        // A highlight up to one side.
        float hx = -r * 0.42f;
        float hy = r * 0.45f;
        float hr = r * 0.16f;
        for (int i = 0; i < 12; i++) {
            float a0 = Mth.TWO_PI * i / 12;
            float a1 = Mth.TWO_PI * (i + 1) / 12;
            put(vc, m, hx, hy, 0xFFFFFF, (int) Math.min(255, 150 * bright));
            put(vc, m, hx + Mth.cos(a0) * hr, hy + Mth.sin(a0) * hr * 0.6f, 0xFFFFFF, 0);
            put(vc, m, hx + Mth.cos(a1) * hr, hy + Mth.sin(a1) * hr * 0.6f, 0xFFFFFF, 0);
            put(vc, m, hx, hy, 0xFFFFFF, (int) Math.min(255, 150 * bright));
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    /** Thin-film colours running round the rim. */
    private static int film(float angle, float time) {
        float h = angle / Mth.TWO_PI + time * 0.01f;
        h -= Mth.floor(h);
        return Mth.hsvToRgb(h, 0.55f, 1.0f);
    }

    private static void put(VertexConsumer vc, Matrix4f m, float x, float y, int rgb, int a) {
        vc.vertex(m, x, y, 0.0f).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Mth.clamp(a, 0, 255)).endVertex();
    }
}
