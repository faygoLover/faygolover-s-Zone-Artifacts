package faygolover.zoneartifacts.client.tesla;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Fire geometry for the Comet and the Razlom, drawn into a {@code RenderType.lightning()} buffer
 * (additive, unlit, back faces culled) like {@link LightningDraw}, but with a width and a colour
 * per point, so tongues of flame can taper and shade from a hot base to a dark red tip.
 */
public final class FireDraw {

    private FireDraw() {
    }

    /** Packs 0..255 channels into one int (ARGB). */
    public static int argb(int a, int r, int g, int b) {
        return (Mth.clamp(a, 0, 255) << 24) | (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
    }

    /** Linear blend of two ARGB colours. */
    public static int mix(int from, int to, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int a = (int) Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return argb(a, r, g, b);
    }

    /** Same colour with its alpha multiplied by {@code factor}. */
    public static int fade(int color, float factor) {
        int a = (int) (((color >>> 24) & 0xFF) * Mth.clamp(factor, 0.0f, 1.0f));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /**
     * A camera-facing strip through {@code points}, {@code halfWidths[i]} wide and coloured
     * {@code colors[i]} (ARGB) at each point. Winding matches {@link LightningDraw#ribbon}, so it
     * survives back-face culling.
     */
    public static void ribbon(Matrix4f matrix, VertexConsumer buffer, Vec3[] points, float[] halfWidths, int[] colors, Vec3 camPos) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 pa = points[i];
            Vec3 pb = points[i + 1];
            Vec3 segDir = pb.subtract(pa);
            double segLen = segDir.length();
            if (segLen < 1.0E-5) continue;
            segDir = segDir.scale(1.0 / segLen);

            Vec3 toCam = camPos.subtract(pa.add(pb).scale(0.5));
            double toCamLen = toCam.length();
            if (toCamLen < 1.0E-4) continue;
            Vec3 side = segDir.cross(toCam.scale(1.0 / toCamLen));
            double sideLen = side.length();
            if (sideLen < 1.0E-4) continue;
            side = side.scale(1.0 / sideLen);

            Vec3 sa = side.scale(halfWidths[i]);
            Vec3 sb = side.scale(halfWidths[i + 1]);
            vertex(buffer, matrix, pa.subtract(sa), colors[i]);
            vertex(buffer, matrix, pa.add(sa), colors[i]);
            vertex(buffer, matrix, pb.add(sb), colors[i + 1]);
            vertex(buffer, matrix, pb.subtract(sb), colors[i + 1]);
        }
    }

    /** Soft round glow facing the camera, colour given as ARGB (alpha = center alpha). */
    public static void glow(Matrix4f matrix, VertexConsumer buffer, Vec3 center, double radius, Vec3 camPos, int color, int slices) {
        LightningDraw.glow(matrix, buffer, center, radius, camPos,
                (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF, slices);
    }

    /** Points along a smooth tongue of flame from {@code base} in direction {@code dir}, bending
     *  towards {@code bend} the further it gets. */
    public static Vec3[] tongue(Vec3 base, Vec3 dir, Vec3 bend, double length, int segments) {
        Vec3[] points = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            points[i] = base.add(dir.scale(length * t)).add(bend.scale(length * t * t));
        }
        return points;
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 p, int color) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF).endVertex();
    }
}
