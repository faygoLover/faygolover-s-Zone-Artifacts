package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaPlacerItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * While holding the Tesla placer: renders every waypoint (small marker cubes, smaller than a full
 * block) and the line connecting each consecutive pair — reusing the exact same drawing approach
 * as {@code AnomalyHighlightRenderer} (via {@link BoxRenderUtil}), just with smaller boxes and an
 * added connecting line. Finalized routes render cyan and are visible to everyone; this client's
 * own in-progress build (if any) renders yellow and is only ever sent to the building player.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT)
public final class TeslaRouteRenderer {

    private static final float MARKER_HALF_SIZE = 0.15f;
    private static final int COLOR_BUILDING = 0xFFE04D; // yellow
    private static final int COLOR_COMPLETE = 0x4DE0FF; // cyan

    private TeslaRouteRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!TeslaPlacerItem.isTeslaPlacer(player.getMainHandItem())
                && !TeslaPlacerItem.isTeslaPlacer(player.getOffhandItem())) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        for (List<BlockPos> route : ClientTeslaCache.routes().values()) {
            renderRoute(poseStack, camPos, route, COLOR_COMPLETE);
        }
        renderRoute(poseStack, camPos, ClientTeslaCache.ownBuildSession(), COLOR_BUILDING);
    }

    private static void renderRoute(PoseStack poseStack, Vec3 camPos, List<BlockPos> points, int color) {
        for (BlockPos p : points) {
            Vec3 center = Vec3.atCenterOf(p).subtract(camPos);
            AABB marker = new AABB(
                    center.x - MARKER_HALF_SIZE, center.y - MARKER_HALF_SIZE, center.z - MARKER_HALF_SIZE,
                    center.x + MARKER_HALF_SIZE, center.y + MARKER_HALF_SIZE, center.z + MARKER_HALF_SIZE);
            BoxRenderUtil.renderFillBox(poseStack, marker, color, 0.55f);
            BoxRenderUtil.renderOutlineBox(poseStack, marker, 1.0f, 1.0f, 1.0f, 0.6f);
        }

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = Vec3.atCenterOf(points.get(i)).subtract(camPos);
            Vec3 bPos = Vec3.atCenterOf(points.get(i + 1)).subtract(camPos);
            BoxRenderUtil.renderLine(poseStack, a, bPos, r, g, b, 0.9f);
        }
    }
}
