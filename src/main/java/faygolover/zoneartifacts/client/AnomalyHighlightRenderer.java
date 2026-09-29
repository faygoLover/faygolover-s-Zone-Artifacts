package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyType;
import faygolover.zoneartifacts.anomaly.AnomalyTypeManager;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * While holding an {@link AnomalyPlacerItem}, draws a translucent volume with opaque edges at
 * the block the player is looking at — a preview of where the anomaly would be placed (or the
 * shape of the one already there).
 * <p>
 * Stage 1 limitation: the client doesn't yet know the real level of an anomaly already placed at
 * the targeted block (that lives in server-side {@code AnomalySavedData}, nothing syncs it to the
 * client yet), so the preview always shows the level-1 size. Good enough to aim placement; once
 * there's a sync packet this can show the true size for an existing anomaly.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnomalyHighlightRenderer {

    private static final int FACE_ARGB = 0x40FFDD55;
    private static final int EDGE_ARGB = 0xFF33251A;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        ResourceLocation typeId = heldAnomalyType(player);
        if (typeId == null) return;

        AnomalyType type = AnomalyTypeManager.get(typeId);
        if (type == null) return;

        if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = blockHit.getBlockPos();

        int size = type.shape().sizeForLevel(1);
        AABB aabb = centeredAabb(pos, size);

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        renderFaces(poseStack, bufferSource.getBuffer(ModRenderTypes.ANOMALY_ZONE_FILL), aabb, FACE_ARGB);
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, aabb,
                argbToFloat(EDGE_ARGB, 16), argbToFloat(EDGE_ARGB, 8), argbToFloat(EDGE_ARGB, 0), argbToFloat(EDGE_ARGB, 24));

        bufferSource.endBatch(RenderType.lines());
        bufferSource.endBatch(ModRenderTypes.ANOMALY_ZONE_FILL);

        poseStack.popPose();
    }

    @Nullable
    private static ResourceLocation heldAnomalyType(Player player) {
        ResourceLocation mainHand = fromStack(player.getMainHandItem());
        if (mainHand != null) return mainHand;
        return fromStack(player.getOffhandItem());
    }

    @Nullable
    private static ResourceLocation fromStack(ItemStack stack) {
        if (stack.getItem() instanceof AnomalyPlacerItem placer) {
            return placer.anomalyTypeId();
        }
        return null;
    }

    private static AABB centeredAabb(BlockPos pos, int size) {
        double half = size / 2.0;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        return new AABB(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
    }

    private static float argbToFloat(int argb, int shift) {
        return ((argb >>> shift) & 0xFF) / 255f;
    }

    private static void renderFaces(PoseStack poseStack, VertexConsumer buffer, AABB box, int argb) {
        float a = argbToFloat(argb, 24);
        float r = argbToFloat(argb, 16);
        float g = argbToFloat(argb, 8);
        float b = argbToFloat(argb, 0);

        PoseStack.Pose pose = poseStack.last();
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

        quad(buffer, pose, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, a); // -X
        quad(buffer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a); // +X
        quad(buffer, pose, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, r, g, b, a); // -Y
        quad(buffer, pose, minX, maxY, maxZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a); // +Y
        quad(buffer, pose, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, minX, minY, minZ, r, g, b, a); // -Z
        quad(buffer, pose, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a); // +Z
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float x3, float y3, float z3, float x4, float y4, float z4,
                              float r, float g, float b, float a) {
        buffer.vertex(pose.pose(), x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(pose.pose(), x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.vertex(pose.pose(), x3, y3, z3).color(r, g, b, a).endVertex();
        buffer.vertex(pose.pose(), x4, y4, z4).color(r, g, b, a).endVertex();
    }
}
