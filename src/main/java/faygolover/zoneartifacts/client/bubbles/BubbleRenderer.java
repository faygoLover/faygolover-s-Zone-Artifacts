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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A soap bubble: a wobbling ball of film, nearly clear in the middle and dense towards its outline,
 * its thin-film colours shifting with the angle, lit by the world like the Amoeba's jelly; a bright
 * little highlight up to one side. Charging, it trembles, swells and brightens.
 */
public class BubbleRenderer extends EntityRenderer<BubbleEntity> {

    private static final ResourceLocation UNUSED = new ResourceLocation(ZoneArtifacts.MODID, "textures/entity/comet.png");
    private static final int RINGS = 12;
    private static final int SEGMENTS = 20;

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
        if (faygolover.zoneartifacts.client.ClientAnomalyCache.hiddenAt(entity.position())) return;
        float time = entity.tickCount + partialTick + entity.getId() * 13.1f;
        float charge = entity.charging() ? 1.0f - Math.max(0, entity.charge()) / 5.0f : 0.0f;
        float r = 0.38f * (1.0f + 0.03f * Mth.sin(time * 0.21f) + 0.25f * charge + (charge > 0 ? 0.04f * Mth.sin(time * 3.1f) : 0.0f));
        float bright = 1.0f + 1.5f * charge;
        float lit = lit(entity, light, partialTick);
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() / 2.0, 0.0);

        // The film itself: a wobbling sphere, nearly clear where one looks straight through it,
        // dense and rainbow-coloured towards its outline (as a real bubble is).
        Vec3 centre = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() / 2.0, 0.0);
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        Matrix4f m = poseStack.last().pose();
        VertexConsumer film = buffers.getBuffer(FilmRenderType.FILM);
        float[][][] pts = new float[RINGS + 1][SEGMENTS + 1][];
        for (int i = 0; i <= RINGS; i++) {
            double theta = Math.PI * i / RINGS;
            for (int j = 0; j <= SEGMENTS; j++) {
                double phi = Math.PI * 2.0 * j / SEGMENTS;
                double nx = Math.sin(theta) * Math.cos(phi);
                double ny = Math.cos(theta);
                double nz = Math.sin(theta) * Math.sin(phi);
                double wob = 1.0 + 0.035 * Math.sin(phi * 3.0 + time * 0.17) * Math.sin(theta * 2.0 + time * 0.11)
                        + (charge > 0 ? 0.03 * Math.sin(theta * 5.0 + time * 2.7) : 0.0);
                float x = (float) (nx * r * wob);
                float y = (float) (ny * r * wob);
                float z = (float) (nz * r * wob);
                Vec3 view = cam.subtract(centre.add(x, y, z));
                double len = view.length();
                double facing = len < 1.0E-5 ? 1.0 : Math.abs((nx * view.x + ny * view.y + nz * view.z) / len);
                double rim = 1.0 - facing;
                float alpha = (float) (0.05 + 0.62 * Math.pow(rim, 2.2)) * Math.min(1.6f, bright);
                float hue = (float) (rim * 1.3 + ny * 0.25 + time * 0.004);
                int rgb = Mth.hsvToRgb(hue - Mth.floor(hue), (float) (0.25 + 0.4 * rim), 1.0f);
                pts[i][j] = new float[]{x, y, z, shadeOf(rgb >> 16, lit), shadeOf(rgb >> 8, lit), shadeOf(rgb, lit), Mth.clamp(alpha, 0.0f, 0.85f)};
            }
        }
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                vertex(film, m, pts[i][j]);
                vertex(film, m, pts[i + 1][j]);
                vertex(film, m, pts[i + 1][j + 1]);
                vertex(film, m, pts[i][j + 1]);
            }
        }

        // A bright little highlight up to one side, facing the camera.
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(GlowRenderType.GLOW);
        float hx = -r * 0.42f;
        float hy = r * 0.45f;
        float hr = r * 0.2f;
        for (int i = 0; i < 12; i++) {
            float a0 = Mth.TWO_PI * i / 12;
            float a1 = Mth.TWO_PI * (i + 1) / 12;
            put(vc, m, hx, hy, 0xFFFFFF, (int) Math.min(255, 200 * bright * lit));
            put(vc, m, hx + Mth.cos(a0) * hr, hy + Mth.sin(a0) * hr * 0.6f, 0xFFFFFF, 0);
            put(vc, m, hx + Mth.cos(a1) * hr, hy + Mth.sin(a1) * hr * 0.6f, 0xFFFFFF, 0);
            put(vc, m, hx, hy, 0xFFFFFF, (int) Math.min(255, 200 * bright * lit));
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    /** How lit it is where it floats (the film isn't a light source; at night it's dim). */
    private static float lit(BubbleEntity entity, int packed, float partial) {
        float block = LightTexture.block(packed) / 15.0f;
        float sky = LightTexture.sky(packed) / 15.0f * ((ClientLevel) entity.level()).getSkyDarken(partial);
        return 0.25f + 0.75f * Math.max(block, sky);
    }

    private static float shadeOf(int channel, float lit) {
        return (channel & 0xFF) / 255.0f * lit;
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float[] p) {
        vc.vertex(m, p[0], p[1], p[2]).color(p[3], p[4], p[5], p[6]).endVertex();
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
