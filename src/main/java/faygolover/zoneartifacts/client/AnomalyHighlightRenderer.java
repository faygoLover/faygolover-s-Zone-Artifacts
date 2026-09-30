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
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.AnomalyDefaults;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import faygolover.zoneartifacts.item.PdaItem;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws anomaly zones as translucent volumes with opaque edges:
 * <ul>
 *     <li>with a placer in hand — every anomaly of that type within {@link #VISIBLE_RADIUS}, plus a
 *     placement preview (default size) where a right-click would put a new one, unless the player
 *     aims into an existing zone or the spot is inside one (placement is refused there);</li>
 *     <li>with a tuner in hand — every anomaly of any type within {@link #TUNER_RADIUS}.</li>
 * </ul>
 * The zone under the crosshair gets white edges.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnomalyHighlightRenderer {

    private static final int FACE_ARGB = 0x40FFDD55;
    private static final int EDGE_ARGB = 0xFF33251A;
    private static final int HOVER_EDGE_ARGB = 0xFFFFFFFF;

    /** How far around the player existing anomalies get drawn with a placer in hand. */
    private static final double VISIBLE_RADIUS = 24.0;

    /** Smaller radius with a tuner, which shows every anomaly type at once. */
    private static final double TUNER_RADIUS = 16.0;

    /** Shrinks the drawn box a hair so its faces don't sit exactly coplanar with terrain faces
     *  (which flickers from z-fighting) when a size-1 zone lines up exactly with a block. */
    private static final double EDGE_INSET = 0.002;

    private record Box(AABB aabb, boolean hovered) {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        boolean tuner = PdaItem.holds(player);
        ResourceLocation typeId = AnomalyPlacerItem.heldTypeId(player);
        if (typeId == null && !tuner) return;

        List<Box> boxes = resolveBoxes(mc, player, tuner ? null : typeId, tuner ? TUNER_RADIUS : VISIBLE_RADIUS, !tuner);
        if (boxes.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        for (Box box : boxes) {
            AABB aabb = box.aabb().deflate(EDGE_INSET);

            renderFilledBox(poseStack, aabb, FACE_ARGB);

            int edge = box.hovered() ? HOVER_EDGE_ARGB : EDGE_ARGB;
            VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(poseStack, lines, aabb,
                    argbToFloat(edge, 16), argbToFloat(edge, 8), argbToFloat(edge, 0), argbToFloat(edge, 24));
            bufferSource.endBatch(RenderType.lines());
        }

        poseStack.popPose();
    }

    /** Zones within {@code radius} ({@code typeId == null}: every type), and — if requested — the
     *  placement preview for a new anomaly. */
    private static List<Box> resolveBoxes(Minecraft mc, Player player, @Nullable ResourceLocation typeId,
                                          double radius, boolean withPreview) {
        List<Box> boxes = new ArrayList<>();
        Vec3 playerPos = player.position();
        double radiusSq = radius * radius;

        List<SyncAnomaliesPacket.Entry> entries = ClientAnomalyCache.allEntriesFor(player.level().dimension());
        Optional<SyncAnomaliesPacket.Entry> hovered = AnomalyClientTargeting.pick(player, typeId);

        BlockPos previewPos = null;
        if (withPreview && hovered.isEmpty()
                && mc.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            // Same resolution as AnomalyPlacerItem.useOn: the preview sits where the anomaly would
            // actually be placed (the neighbour on the clicked face's side), not inside the clicked block.
            previewPos = AnomalyTypeIds.SWAMP.equals(typeId)
                    ? blockHit.getBlockPos() // the swamp goes into the clicked block itself
                    : new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), blockHit).getClickedPos();
        }

        for (SyncAnomaliesPacket.Entry entry : entries) {
            AABB zone = AnomalyGeometry.zoneAabb(entry);
            if (previewPos != null && (entry.pos().equals(previewPos) || AnomalyGeometry.containsBlockCenter(zone, previewPos))) {
                previewPos = null;
            }
            if (typeId != null && !entry.typeId().equals(typeId)) continue;

            double dx = entry.pos().getX() + 0.5 - playerPos.x;
            double dy = entry.pos().getY() + 0.5 - playerPos.y;
            double dz = entry.pos().getZ() + 0.5 - playerPos.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

            boolean isHovered = hovered.isPresent() && hovered.get().pos().equals(entry.pos()) && hovered.get().typeId().equals(entry.typeId());
            boxes.add(new Box(zone, isHovered));
        }

        if (previewPos != null) {
            boxes.add(new Box(typeId != null ? AnomalyGeometry.zoneAabb(typeId, previewPos, AnomalyDefaults.size(typeId))
                    : AnomalyGeometry.centeredAabb(previewPos, AnomalyDefaults.SIZE), false));
        }
        return boxes;
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
