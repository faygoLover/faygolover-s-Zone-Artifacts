package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.zoneartifacts.entity.TeslaWaypointEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * A deliberate no-op, exactly like {@link TeslaEntityRenderer}: the actual visual for a waypoint
 * marker - a small box, part of a glowing line - is drawn globally by {@link
 * TeslaRouteHighlightRenderer} while the route tool is held, not per-entity model geometry. This
 * class only exists to satisfy Forge's "every registered entity type needs a renderer" requirement.
 */
public class TeslaWaypointEntityRenderer extends EntityRenderer<TeslaWaypointEntity> {

    public TeslaWaypointEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TeslaWaypointEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        // Intentionally empty - see class javadoc.
    }

    @Override
    public ResourceLocation getTextureLocation(TeslaWaypointEntity entity) {
        return MissingTextureAtlasSprite.getLocation();
    }
}
