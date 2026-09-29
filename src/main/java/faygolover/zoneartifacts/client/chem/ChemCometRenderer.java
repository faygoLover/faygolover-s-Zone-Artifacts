package faygolover.zoneartifacts.client.chem;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.ChemCometEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** The Chemical Comet draws nothing itself: its gas body is handed to {@link Gas} by {@link ChemClient}. */
public class ChemCometRenderer extends EntityRenderer<ChemCometEntity> {

    private static final ResourceLocation UNUSED_TEXTURE = new ResourceLocation(ZoneArtifacts.MODID, "textures/entity/comet.png");

    public ChemCometRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
        ChemClient.init();
    }

    @Override
    public ResourceLocation getTextureLocation(ChemCometEntity entity) {
        return UNUSED_TEXTURE;
    }

    @Override
    public void render(ChemCometEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
    }
}
