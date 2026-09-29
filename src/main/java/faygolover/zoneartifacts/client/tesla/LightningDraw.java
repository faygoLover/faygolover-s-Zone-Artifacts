package faygolover.zoneartifacts.client.tesla;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Lightning geometry for the Tesla, drawn into a {@code RenderType.lightning()} buffer (additive,
 * unlit, back faces culled). The jitter and camera-facing ribbon are the same technique Electra's
 * {@code AnomalyArcRenderer} uses; kept as a separate copy with alpha support so Electra's
 * renderer stays untouched.
 */
public final class LightningDraw {

    private LightningDraw() {
    }

    /** A polyline from {@code start} to {@code end}, jittered sideways with a sine envelope so it
     *  always lands exactly on both ends however jagged the middle gets. */
    public static Vec3[] jittered(Vec3 start, Vec3 end, RandomSource rand, int segments, double jitterFraction) {
        Vec3 dir = end.subtract(start);
        double len = dir.length();
        Vec3[] points = new Vec3[segments + 1];
        if (len < 1.0E-4) {
            for (int i = 0; i <= segments; i++) points[i] = start;
            return points;
        }
        Vec3 dirNorm = dir.scale(1.0 / len);
        Vec3 arbitrary = Math.abs(dirNorm.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp1 = dirNorm.cross(arbitrary).normalize();
        Vec3 perp2 = dirNorm.cross(perp1).normalize();

        for (int j = 0; j <= segments; j++) {
            double t = j / (double) segments;
            Vec3 base = start.add(dir.scale(t));
            if (j == 0 || j == segments) {
                points[j] = base;
            } else {
                double scale = Math.sin(Math.PI * t) * len * jitterFraction;
                double a = (rand.nextDouble() - 0.5) * 2.0 * scale;
                double b = (rand.nextDouble() - 0.5) * 2.0 * scale;
                points[j] = base.add(perp1.scale(a)).add(perp2.scale(b));
            }
        }
        return points;
    }

    /** Each segment as a quad turned toward the camera around the segment's own axis. Winding is
     *  counter-clockwise as seen from the camera, so the quads survive back-face culling. */
    public static void ribbon(Matrix4f matrix, VertexConsumer buffer, Vec3[] points, float halfWidth,
                              int r, int g, int b, int a, Vec3 camPos) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 pa = points[i];
            Vec3 pb = points[i + 1];
            Vec3 toCam = camPos.subtract(pa.add(pb).scale(0.5));
            double toCamLen = toCam.length();
            if (toCamLen < 1.0E-4) continue;
            toCam = toCam.scale(1.0 / toCamLen);

            Vec3 segDir = pb.subtract(pa);
            double segLen = segDir.length();
            if (segLen < 1.0E-4) continue;
            segDir = segDir.scale(1.0 / segLen);

            Vec3 side = segDir.cross(toCam);
            double sideLen = side.length();
            if (sideLen < 1.0E-4) continue;
            side = side.scale(halfWidth / sideLen);

            vertex(buffer, matrix, pa.subtract(side), r, g, b, a);
            vertex(buffer, matrix, pa.add(side), r, g, b, a);
            vertex(buffer, matrix, pb.add(side), r, g, b, a);
            vertex(buffer, matrix, pb.subtract(side), r, g, b, a);
        }
    }

    /**
     * A soft round glow facing the camera: a fan from a bright center fading to fully transparent
     * at the rim. Each fan slice is a quad with a repeated corner (i.e. a triangle), wound so its
     * front faces the camera.
     */
    public static void glow(Matrix4f matrix, VertexConsumer buffer, Vec3 center, double radius, Vec3 camPos,
                            int r, int g, int b, int centerAlpha, int slices) {
        Vec3 toCam = camPos.subtract(center);
        double len = toCam.length();
        if (len < 1.0E-4 || radius <= 0) return;
        toCam = toCam.scale(1.0 / len);
        Vec3 up = Math.abs(toCam.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = toCam.cross(up).normalize();
        Vec3 upOnPlane = right.cross(toCam).normalize();

        Vec3 prev = rim(center, right, upOnPlane, radius, 0);
        for (int i = 1; i <= slices; i++) {
            Vec3 next = rim(center, right, upOnPlane, radius, 2.0 * Math.PI * i / slices);
            vertex(buffer, matrix, center, r, g, b, centerAlpha);
            vertex(buffer, matrix, next, r, g, b, 0);
            vertex(buffer, matrix, prev, r, g, b, 0);
            vertex(buffer, matrix, prev, r, g, b, 0);
            prev = next;
        }
    }

    private static Vec3 rim(Vec3 center, Vec3 right, Vec3 up, double radius, double angle) {
        return center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int r, int g, int b, int a) {
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(r, g, b, a).endVertex();
    }
}
