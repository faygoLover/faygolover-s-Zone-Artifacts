package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared lightning-drawing helpers used by both {@link AnomalyArcRenderer} (Electra's ambient
 * arcs and strikes) and the Tesla effect renderer (its ball-of-lightning, block-burst and
 * entity-electrification visuals) — one jitter/billboard/anchor-sampling implementation instead
 * of two copies that could drift apart.
 */
public final class LightningRenderUtil {

    private LightningRenderUtil() {
    }

    /**
     * Anchors sampled on real block-collision faces touching {@code aabb}: for every non-solid
     * cell inside/around it, each solid neighbor contributes the midpoint of the shared face.
     * Falls back to plain random points inside the box when nothing solid is nearby (a zone or
     * entity floating in open air/sky).
     */
    public static List<Vec3> findSurfacePoints(ClientLevel level, AABB aabb, int count, RandomSource random) {
        List<Vec3> candidates = new ArrayList<>();

        BlockPos min = new BlockPos(Mth.floor(aabb.minX - 1), Mth.floor(aabb.minY - 1), Mth.floor(aabb.minZ - 1));
        BlockPos max = new BlockPos(Mth.floor(aabb.maxX + 1), Mth.floor(aabb.maxY + 1), Mth.floor(aabb.maxZ + 1));

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (!isOpen(level, cursor)) continue;
                    if (!aabb.intersects(new AABB(cursor))) continue;

                    for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                        BlockPos neighbor = cursor.relative(dir);
                        if (isOpen(level, neighbor)) continue;

                        double fx = cursor.getX() + 0.5 + dir.getStepX() * 0.5;
                        double fy = cursor.getY() + 0.5 + dir.getStepY() * 0.5;
                        double fz = cursor.getZ() + 0.5 + dir.getStepZ() * 0.5;
                        Vec3 facePoint = new Vec3(fx, fy, fz);
                        if (aabb.contains(facePoint.x, facePoint.y, facePoint.z)) {
                            candidates.add(facePoint);
                        }
                    }
                }
            }
        }

        List<Vec3> result = new ArrayList<>(count);
        if (candidates.isEmpty()) {
            for (int i = 0; i < count; i++) {
                result.add(new Vec3(
                        lerp(aabb.minX, aabb.maxX, random.nextDouble()),
                        lerp(aabb.minY, aabb.maxY, random.nextDouble()),
                        lerp(aabb.minZ, aabb.maxZ, random.nextDouble())));
            }
        } else {
            for (int i = 0; i < count; i++) {
                result.add(candidates.get(random.nextInt(candidates.size())));
            }
        }
        return result;
    }

    /** Random points on the six faces of an arbitrary AABB — used to anchor arcs onto an entity's own body. */
    public static List<Vec3> surfacePointsOfBox(AABB box, int count, RandomSource random) {
        List<Vec3> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int face = random.nextInt(6);
            double u = random.nextDouble();
            double v = random.nextDouble();
            double x, y, z;
            switch (face) {
                case 0 -> { x = box.minX; y = lerp(box.minY, box.maxY, u); z = lerp(box.minZ, box.maxZ, v); }
                case 1 -> { x = box.maxX; y = lerp(box.minY, box.maxY, u); z = lerp(box.minZ, box.maxZ, v); }
                case 2 -> { x = lerp(box.minX, box.maxX, u); y = box.minY; z = lerp(box.minZ, box.maxZ, v); }
                case 3 -> { x = lerp(box.minX, box.maxX, u); y = box.maxY; z = lerp(box.minZ, box.maxZ, v); }
                case 4 -> { x = lerp(box.minX, box.maxX, u); y = lerp(box.minY, box.maxY, v); z = box.minZ; }
                default -> { x = lerp(box.minX, box.maxX, u); y = lerp(box.minY, box.maxY, v); z = box.maxZ; }
            }
            result.add(new Vec3(x, y, z));
        }
        return result;
    }

    public static boolean isOpen(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Subdivides each link between consecutive anchors, jittering the intermediate points
     * perpendicular to the link with a sine envelope (zero at both ends, so every subdivided
     * segment still lands exactly on its real anchors regardless of jitter magnitude).
     */
    public static List<Vec3> buildJitteredPoints(List<Vec3> anchors, long seed, int variant, int segmentsPerLink, double amplitude) {
        List<Vec3> result = new ArrayList<>();
        if (anchors.isEmpty()) return result;
        result.add(anchors.get(0));

        RandomSource random = RandomSource.create(seed + variant * 7919L);
        for (int i = 0; i + 1 < anchors.size(); i++) {
            Vec3 a = anchors.get(i);
            Vec3 b = anchors.get(i + 1);
            Vec3 link = b.subtract(a);
            double length = link.length();
            if (length < 1.0E-4) continue;
            Vec3 dir = link.scale(1.0 / length);
            Vec3 perpA = perpendicular(dir);
            Vec3 perpB = dir.cross(perpA).normalize();

            for (int s = 1; s <= segmentsPerLink; s++) {
                double t = (double) s / (segmentsPerLink + 1);
                double envelope = Math.sin(Math.PI * t);
                double jitterA = (random.nextDouble() * 2 - 1) * amplitude * envelope;
                double jitterB = (random.nextDouble() * 2 - 1) * amplitude * envelope;
                Vec3 point = a.add(link.scale(t)).add(perpA.scale(jitterA)).add(perpB.scale(jitterB));
                result.add(point);
            }
            result.add(b);
        }
        return result;
    }

    public static Vec3 perpendicular(Vec3 dir) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 cross = dir.cross(up);
        if (cross.lengthSqr() < 1.0E-6) {
            cross = dir.cross(new Vec3(1, 0, 0));
        }
        return cross.normalize();
    }

    /** Camera-facing billboarded ribbon quads, one per polyline segment, in a buffer already relative to the camera. */
    public static void renderPolylineQuads(VertexConsumer buffer, Matrix4f matrix, List<Vec3> points,
                                            Vec3 camPos, Vec3 camLook, int color, float thickness) {
        if (points.size() < 2) return;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i).subtract(camPos);
            Vec3 bPos = points.get(i + 1).subtract(camPos);
            Vec3 dir = bPos.subtract(a);
            if (dir.lengthSqr() < 1.0E-8) continue;
            dir = dir.normalize();

            Vec3 right = dir.cross(camLook);
            if (right.lengthSqr() < 1.0E-6) {
                right = dir.cross(new Vec3(0, 1, 0));
            }
            right = right.normalize().scale(thickness / 2.0);

            Vec3 a1 = a.subtract(right);
            Vec3 a2 = a.add(right);
            Vec3 b1 = bPos.add(right);
            Vec3 b2 = bPos.subtract(right);

            buffer.vertex(matrix, (float) a1.x, (float) a1.y, (float) a1.z).color(r, g, b, 1.0f).endVertex();
            buffer.vertex(matrix, (float) a2.x, (float) a2.y, (float) a2.z).color(r, g, b, 1.0f).endVertex();
            buffer.vertex(matrix, (float) b1.x, (float) b1.y, (float) b1.z).color(r, g, b, 1.0f).endVertex();
            buffer.vertex(matrix, (float) b2.x, (float) b2.y, (float) b2.z).color(r, g, b, 1.0f).endVertex();
        }
    }
}
