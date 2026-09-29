package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * While holding an {@link AnomalyPlacerItem}, draws a translucent volume with opaque edges:
 * either the real zone of an existing anomaly of this type the player is aiming into (resolved
 * via {@link AnomalyClientTargeting} against the server-synced {@link ClientAnomalyCache}), or,
 * failing that, a level-1 placement preview at the currently targeted block.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnomalyHighlightRenderer {

    private static final int FACE_ARGB = 0x40FFDD55;
    private static final int EDGE_ARGB = 0xFF33251A;

    /** Shrinks the drawn box a hair so its faces don't sit exactly coplanar with terrain faces
     *  (which flickers from z-fighting) when a size-1 zone lines up exactly with a block. */
    private static final double EDGE_INSET = 0.002;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        ResourceLocation typeId = AnomalyPlacerItem.heldTypeId(player);
        if (typeId == null) return;

        AABB aabb = resolveAabbToShow(mc, player, typeId);
        if (aabb == null) return;
        aabb = aabb.deflate(EDGE_INSET);

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        renderFilledBox(poseStack, aabb, FACE_ARGB);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, aabb,
                argbToFloat(EDGE_ARGB, 16), argbToFloat(EDGE_ARGB, 8), argbToFloat(EDGE_ARGB, 0), argbToFloat(EDGE_ARGB, 24));
        bufferSource.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    /** The real zone of an existing anomaly the player is aiming into, if any; otherwise a
     *  level-1 placement preview at whatever block they're looking at. */
    @Nullable
    private static AABB resolveAabbToShow(Minecraft mc, Player player, ResourceLocation typeId) {
        Optional<SyncAnomaliesPacket.Entry> hit = AnomalyClientTargeting.pick(player, typeId);
        if (hit.isPresent()) {
            SyncAnomaliesPacket.Entry entry = hit.get();
            int size = ClientAnomalyTypeCache.sizeForLevel(entry.typeId(), entry.level());
            return AnomalyGeometry.centeredAabb(entry.pos(), size);
        }

        if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        int size = ClientAnomalyTypeCache.sizeForLevel(typeId, 1);
        return AnomalyGeometry.centeredAabb(pos, size);
    }

    private static float argbToFloat(int argb, int shift) {
        return ((argb >>> shift) & 0xFF) / 255f;
    }

    /**
     * Draws the translucent fill in immediate mode (Tesselator + RenderSystem) instead of going
     * through a custom RenderType: RenderType's own shard constants (POSITION_COLOR_SHADER,
     * TRANSLUCENT_TRANSPARENCY, NO_CULL, ...) are declared {@code protected} on RenderStateShard,
     * so a mod can't reference them without subclassing tricks. This does the same GL state
     * (position+color shader, alpha blending, no culling, no depth write) by hand instead.
     */
    private static void renderFilledBox(PoseStack poseStack, AABB box, int argb) {
        float a = argbToFloat(argb, 24);
        float r = argbToFloat(argb, 16);
        float g = argbToFloat(argb, 8);
        float b = argbToFloat(argb, 0);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

        quad(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, a); // -X
        quad(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a); // +X
        quad(buffer, matrix, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, r, g, b, a); // -Y
        quad(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a); // +Y
        quad(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, minX, minY, minZ, r, g, b, a); // -Z
        quad(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a); // +Z

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float x3, float y3, float z3, float x4, float y4, float z4,
                              float r, float g, float b, float a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x4, y4, z4).color(r, g, b, a).endVertex();
    }
}
