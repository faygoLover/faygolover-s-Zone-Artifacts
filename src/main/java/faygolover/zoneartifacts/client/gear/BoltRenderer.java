package faygolover.zoneartifacts.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import faygolover.zoneartifacts.gear.BoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** A thrown bolt or nut: its own 3D item model, tumbling as it flies. */
public class BoltRenderer extends EntityRenderer<BoltEntity> {

    private final ItemRenderer items;

    public BoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.03f;
    }

    @Override
    public void render(BoltEntity bolt, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0.0, 0.03, 0.0);
        float spin = (bolt.tickCount + partial) * 28.0f + bolt.getId() * 37.0f;
        pose.mulPose(Axis.YP.rotationDegrees(spin * 0.6f));
        pose.mulPose(Axis.XP.rotationDegrees(spin));
        items.renderStatic(bolt.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, bolt.level(), bolt.getId());
        pose.popPose();
        super.render(bolt, yaw, partial, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(BoltEntity bolt) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
