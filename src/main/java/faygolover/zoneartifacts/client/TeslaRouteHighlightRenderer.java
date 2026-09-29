package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaRouteToolItem;
import faygolover.zoneartifacts.network.SyncTeslaRoutesPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * While holding {@link TeslaRouteToolItem}, draws the route being planned: a small wireframe box
 * at every waypoint plus a glowing line connecting them in order — an open polyline for the
 * in-progress chain (read straight from the held stack's own NBT, no packet needed), and a closed
 * loop for every already-completed route within {@link #VISIBLE_RADIUS} (from {@link
 * ClientTeslaRouteCache}, synced by {@code TeslaRouteSyncHandler}). The same kind of always-visible
 * feedback {@code AnomalyHighlightRenderer} gives Electra's placer, adapted from "one box" to
 * "a chain of points."
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TeslaRouteHighlightRenderer {

    private static final double VISIBLE_RADIUS = 48.0;
    private static final float LINE_HALF_WIDTH = 0.03f;
    private static final double WAYPOINT_BOX_INFLATE = 0.03;

    /** In-progress chain: warm amber, reading as "still being built." */
    private static final int CHAIN_ARGB = 0xFFFFC966;
    /** A completed, persisted route: cool cyan, matching Tesla's own arc color family. */
    private static final int ROUTE_ARGB = 0xFF66E0FF;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        ItemStack stack = TeslaRouteToolItem.heldStack(player);
        if (stack == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        List<BlockPos> chain = TeslaRouteToolItem.getChain(stack);
        if (!chain.isEmpty()) {
            drawPolyline(poseStack, bufferSource, camPos, chain, false, CHAIN_ARGB);
        }

        double radiusSq = VISIBLE_RADIUS * VISIBLE_RADIUS;
        Vec3 playerPos = player.position();
        for (SyncTeslaRoutesPacket.Entry entry : ClientTeslaRouteCache.entriesFor(player.level().dimension())) {
            List<BlockPos> waypoints = entry.waypoints();
            if (waypoints.isEmpty()) continue;

            BlockPos first = waypoints.get(0);
            double dx = first.getX() + 0.5 - playerPos.x;
            double dy = first.getY() + 0.5 - playerPos.y;
            double dz = first.getZ() + 0.5 - playerPos.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

            drawPolyline(poseStack, bufferSource, camPos, waypoints, true, ROUTE_ARGB);
        }

        poseStack.popPose();
    }

    private static void drawPolyline(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos,
                                      List<BlockPos> waypoints, boolean closed, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        for (BlockPos pos : waypoints) {
            AABB box = new AABB(pos).inflate(WAYPOINT_BOX_INFLATE);
            LevelRenderer.renderLineBox(poseStack, lines, box, r / 255f, g / 255f, b / 255f, a);
        }
        bufferSource.endBatch(RenderType.lines());

        if (waypoints.size() < 2) return;

        // Connecting line(s): drawn as thin glowing quads (same billboard-per-segment technique as
        // Tesla's own arcs), which only need a position+color format — no per-vertex normal like
        // RenderType.lines() would need for an arbitrary (non-axis-aligned) segment.
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer glow = bufferSource.getBuffer(RenderType.lightning());
        int segments = closed ? waypoints.size() : waypoints.size() - 1;
        for (int i = 0; i < segments; i++) {
            Vec3 from = Vec3.atCenterOf(waypoints.get(i));
            Vec3 to = Vec3.atCenterOf(waypoints.get((i + 1) % waypoints.size()));
            renderThickLine(matrix, glow, from, to, r, g, b, camPos);
        }
        bufferSource.endBatch(RenderType.lightning());
    }

    private static void renderThickLine(Matrix4f matrix, VertexConsumer buffer, Vec3 pa, Vec3 pb,
                                         int r, int g, int b, Vec3 camPos) {
        Vec3 mid = pa.add(pb).scale(0.5);
        Vec3 toCam = camPos.subtract(mid);
        double toCamLen = toCam.length();
        if (toCamLen < 1.0E-4) return;
        toCam = toCam.scale(1.0 / toCamLen);

        Vec3 segDir = pb.subtract(pa);
        double segLen = segDir.length();
        if (segLen < 1.0E-4) return;
        segDir = segDir.scale(1.0 / segLen);

        Vec3 side = segDir.cross(toCam);
        double sideLen = side.length();
        if (sideLen < 1.0E-4) return;
        side = side.scale(LINE_HALF_WIDTH / sideLen);

        vertex(buffer, matrix, pa.subtract(side), r, g, b);
        vertex(buffer, matrix, pa.add(side), r, g, b);
        vertex(buffer, matrix, pb.add(side), r, g, b);
        vertex(buffer, matrix, pb.subtract(side), r, g, b);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int r, int g, int b) {
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(r, g, b, 255).endVertex();
    }

    private TeslaRouteHighlightRenderer() {
    }
}
