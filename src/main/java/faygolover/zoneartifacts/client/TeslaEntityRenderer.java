package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * A deliberately empty {@link EntityRenderer} — vanilla requires every registered {@link
 * net.minecraft.world.entity.EntityType} to have one, but Tesla's actual visuals (the lightning
 * ball, glow core, block bursts, electrification) are all drawn by {@link TeslaEffectRenderer}
 * during {@code RenderLevelStageEvent} instead, which is a far more natural fit for camera-facing
 * billboarded lightning geometry than a per-entity model would be.
 */
public class TeslaEntityRenderer extends EntityRenderer<TeslaEntity> {

    private static final ResourceLocation BLANK_TEXTURE =
            new ResourceLocation(ZoneArtifacts.MODID, "textures/entity/blank.png");

    public TeslaEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TeslaEntity entity) {
        return BLANK_TEXTURE;
    }

    @Override
    public void render(TeslaEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        // Intentionally empty — see class javadoc.
    }
}
