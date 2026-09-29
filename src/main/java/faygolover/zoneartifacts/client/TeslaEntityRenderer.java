package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.zoneartifacts.entity.TeslaEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * A deliberate no-op. Every visible thing about a Tesla — the lightning ball, the electrify wrap
 * on whatever she hits, the bump discharge — is drawn globally by {@link TeslaVisualRenderer} from
 * server-sent packets and her own synced position, not per-entity model geometry. This class only
 * exists to satisfy Forge's "every registered entity type needs a renderer" requirement, and
 * deliberately draws (and names) nothing at all.
 */
public class TeslaEntityRenderer extends EntityRenderer<TeslaEntity> {

    public TeslaEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TeslaEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        // Intentionally empty - see class javadoc.
    }

    @Override
    public ResourceLocation getTextureLocation(TeslaEntity entity) {
        return MissingTextureAtlasSprite.getLocation();
    }
}
