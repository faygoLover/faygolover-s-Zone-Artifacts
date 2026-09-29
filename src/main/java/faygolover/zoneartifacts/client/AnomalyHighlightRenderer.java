package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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

import java.util.Optional;

/**
 * While holding a placer item: draws a translucent fill over every existing anomaly of that type
 * within range (not just the exact anchor block that was clicked to place it — the whole zone, so
 * a size-2/3 zone is fully visible), brighter for whichever one is currently targeted, plus a thin
 * outline at the spot a new one would be placed (adjacent to the aimed-at face) when nothing
 * existing is being targeted. Drawing itself goes through {@link BoxRenderUtil}.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT)
public final class AnomalyHighlightRenderer {

    private static final double RENDER_DISTANCE = 48.0;
    private static final int DEFAULT_COLOR = 0xB8E8FF;

    private AnomalyHighlightRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ResourceLocation typeId = AnomalyPlacerItem.typeIdOf(player.getMainHandItem());
        if (typeId == null) {
            typeId = AnomalyPlacerItem.typeIdOf(player.getOffhandItem());
        }
        if (typeId == null) return;

        SyncAnomalyTypeShapesPacket.TypeShape shape = ClientAnomalyTypeCache.get(typeId);
        if (shape == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        Optional<ClientAnomalyCache.Entry> targeted = AnomalyClientTargeting.pick(typeId);
        int color = shape.arc() != null ? shape.arc().color() : DEFAULT_COLOR;

        for (ClientAnomalyCache.Entry entry : ClientAnomalyCache.ofType(typeId)) {
            double distSq = Vec3.atCenterOf(entry.pos()).distanceToSqr(camPos);
            if (distSq > RENDER_DISTANCE * RENDER_DISTANCE) continue;

            int size = ClientAnomalyTypeCache.sizeForLevel(shape, entry.level());
            AABB aabb = AnomalyGeometry.centeredAabb(entry.pos(), size).move(-camPos.x, -camPos.y, -camPos.z);

            boolean isTargeted = targeted.isPresent() && targeted.get().key().equals(entry.key());
            BoxRenderUtil.renderFillBox(poseStack, aabb, color, isTargeted ? 0.35f : 0.16f);
        }

        if (targeted.isEmpty() && mc.hitResult instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos placeAt = blockHit.getBlockPos().relative(blockHit.getDirection());
            AABB previewBox = new AABB(placeAt).move(-camPos.x, -camPos.y, -camPos.z);
            BoxRenderUtil.renderOutlineBox(poseStack, previewBox, 1.0f, 1.0f, 1.0f, 0.8f);
        }
    }
}
