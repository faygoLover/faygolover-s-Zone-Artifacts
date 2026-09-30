package faygolover.zoneartifacts.client.gravi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.PdaItem;
import faygolover.zoneartifacts.item.TeslaRoutePlacerItem;
import faygolover.zoneartifacts.tesla.GraviEntity;
import faygolover.zoneartifacts.tesla.RouteKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Gravi is invisible. Only someone holding a tuner or a Gravi route placer sees where it is: a
 * small violet box (and a faint one showing its reach).
 */
public class GraviRenderer extends EntityRenderer<GraviEntity> {

    private static final ResourceLocation UNUSED_TEXTURE = new ResourceLocation(ZoneArtifacts.MODID, "textures/entity/comet.png");

    public GraviRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(GraviEntity entity) {
        return UNUSED_TEXTURE;
    }

    @Override
    public void render(GraviEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !entity.getState().isVisible()) return;
        boolean show = PdaItem.holds(player) || TeslaRoutePlacerItem.heldKind(player) == RouteKind.GRAVI;
        if (!show) return;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        double h = entity.getBbHeight();
        double w = entity.getBbWidth() / 2.0;
        LevelRenderer.renderLineBox(poseStack, lines, new AABB(-w, 0, -w, w, h, w), 0.75f, 0.45f, 1.0f, 1.0f);
        double r = entity.getSize();
        double cy = h / 2.0;
        LevelRenderer.renderLineBox(poseStack, lines, new AABB(-r, cy - r, -r, r, cy + r, r), 0.75f, 0.45f, 1.0f, 0.25f);
    }

    @Override
    public boolean shouldRender(GraviEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }
}
