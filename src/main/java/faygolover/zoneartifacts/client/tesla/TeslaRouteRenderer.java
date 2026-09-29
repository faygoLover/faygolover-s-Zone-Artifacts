package faygolover.zoneartifacts.client.tesla;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.item.TeslaRoutePlacerItem;
import faygolover.zoneartifacts.network.SyncTeslaRoutesPacket;
import faygolover.zoneartifacts.tesla.TeslaGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
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
import java.util.List;
import java.util.Optional;

/**
 * While the Tesla placer is in hand: every route around the player as small waypoint cubes joined
 * by route lines — yellow while still being built, cyan once completed (a completed route also
 * gets its closing line from the last point back to the first). A draft's start point is drawn a
 * bit larger, so the GM always sees which point closes the route. The waypoint under the
 * crosshair gets white edges, and a faint cube shows where the next point would go.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeslaRouteRenderer {

    private static final double VISIBLE_RADIUS = 48.0;

    private static final int DRAFT_RGB = 0xFFD23C;
    private static final int COMPLETE_RGB = 0x46C8FF;
    private static final int FILL_ALPHA = 0x60;
    private static final int PREVIEW_ALPHA = 0x28;

    private TeslaRouteRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !TeslaRoutePlacerItem.isHeld(player)) return;

        List<SyncTeslaRoutesPacket.Entry> routes = TeslaClientCache.routesFor(mc.level.dimension());
        Optional<TeslaGeometry.WaypointHit> aimed = TeslaClientHandler.pick(player);
        if (aimed.isPresent() && !TeslaGeometry.beatsBlock(aimed.get(), TeslaClientHandler.blockHitDistanceSq(player))) {
            aimed = Optional.empty();
        }
        BlockPos preview = aimed.isPresent() ? null : previewPos(mc, player, routes);

        Vec3 camPos = event.getCamera().getPosition();
        Vec3 playerPos = player.position();
        double radiusSq = VISIBLE_RADIUS * VISIBLE_RADIUS;
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Translucent fills first (immediate mode, like Electra's highlight box)...
        for (SyncTeslaRoutesPacket.Entry route : routes) {
            int rgb = route.complete() ? COMPLETE_RGB : DRAFT_RGB;
            List<BlockPos> points = route.points();
            for (int i = 0; i < points.size(); i++) {
                if (TeslaGeometry.center(points.get(i)).distanceToSqr(playerPos) > radiusSq) continue;
                fillBox(poseStack, markerBox(route, i), rgb, FILL_ALPHA);
            }
        }
        if (preview != null) {
            fillBox(poseStack, TeslaGeometry.cube(TeslaGeometry.center(preview), TeslaGeometry.MARKER_SIZE), DRAFT_RGB, PREVIEW_ALPHA);
        }

        // ...then all edges and route lines in one line batch.
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        for (SyncTeslaRoutesPacket.Entry route : routes) {
            int rgb = route.complete() ? COMPLETE_RGB : DRAFT_RGB;
            List<BlockPos> points = route.points();
            for (int i = 0; i < points.size(); i++) {
                Vec3 c = TeslaGeometry.center(points.get(i));
                boolean near = c.distanceToSqr(playerPos) <= radiusSq;
                if (near) {
                    boolean hovered = aimed.isPresent() && aimed.get().ref().routeId() == route.id() && aimed.get().ref().index() == i;
                    int edge = hovered ? 0xFFFFFF : rgb;
                    LevelRenderer.renderLineBox(poseStack, lines, markerBox(route, i),
                            r(edge), g(edge), b(edge), 1.0f);
                }
                boolean hasNext = i + 1 < points.size();
                boolean closing = !hasNext && route.complete() && points.size() > 1;
                if (!hasNext && !closing) continue;
                Vec3 next = TeslaGeometry.center(points.get(hasNext ? i + 1 : 0));
                if (!near && next.distanceToSqr(playerPos) > radiusSq) continue;
                line(pose, lines, c, next, rgb);
            }
        }
        if (preview != null) {
            LevelRenderer.renderLineBox(poseStack, lines, TeslaGeometry.cube(TeslaGeometry.center(preview), TeslaGeometry.MARKER_SIZE),
                    r(DRAFT_RGB), g(DRAFT_RGB), b(DRAFT_RGB), 0.45f);
        }
        bufferSource.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    private static AABB markerBox(SyncTeslaRoutesPacket.Entry route, int index) {
        double size = !route.complete() && index == 0 ? TeslaGeometry.START_MARKER_SIZE : TeslaGeometry.MARKER_SIZE;
        return TeslaGeometry.cube(TeslaGeometry.center(route.points().get(index)), size);
    }

    /** Where a right-click would put the next waypoint, unless a waypoint already sits there. */
    @Nullable
    private static BlockPos previewPos(Minecraft mc, Player player, List<SyncTeslaRoutesPacket.Entry> routes) {
        if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) return null;
        InteractionHand hand = player.getMainHandItem().getItem() instanceof TeslaRoutePlacerItem
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        BlockPos pos = new BlockPlaceContext(player, hand, player.getItemInHand(hand), blockHit).getClickedPos();
        for (SyncTeslaRoutesPacket.Entry route : routes) {
            if (route.points().contains(pos)) return null;
        }
        return pos;
    }

    private static void line(PoseStack.Pose pose, VertexConsumer lines, Vec3 from, Vec3 to, int rgb) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1.0E-4) return;
        float nx = (float) (dir.x / len), ny = (float) (dir.y / len), nz = (float) (dir.z / len);
        lines.vertex(pose.pose(), (float) from.x, (float) from.y, (float) from.z)
                .color(r(rgb), g(rgb), b(rgb), 1.0f).normal(pose.normal(), nx, ny, nz).endVertex();
        lines.vertex(pose.pose(), (float) to.x, (float) to.y, (float) to.z)
                .color(r(rgb), g(rgb), b(rgb), 1.0f).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    private static float r(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255f;
    }

    private static float g(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255f;
    }

    private static float b(int rgb) {
        return (rgb & 0xFF) / 255f;
    }

    /** Translucent box fill in immediate mode — same approach and GL state as Electra's
     *  {@code AnomalyHighlightRenderer.renderFilledBox}, kept as a separate copy. */
    private static void fillBox(PoseStack poseStack, AABB box, int rgb, int alpha) {
        float r = r(rgb), g = g(rgb), b = b(rgb), a = alpha / 255f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = poseStack.last().pose();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        quad(buffer, m, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, a);
        quad(buffer, m, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, r, g, b, a);
        quad(buffer, m, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, r, g, b, a);
        quad(buffer, m, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, r, g, b, a);
        quad(buffer, m, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, r, g, b, a);
        quad(buffer, m, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a);

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buffer, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        buffer.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        buffer.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        buffer.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        buffer.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
    }
}
