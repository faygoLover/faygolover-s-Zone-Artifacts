package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.client.tesla.FireDraw;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Glows collected while drawing flames and bolts, then drawn together in one
 * {@link GlowRenderType#GLOW} pass — for renderers that loop over many things, each with both.
 */
public final class GlowBatch {

    private record Glow(Vec3 center, double radius, int color, int slices) {
    }

    private final List<Glow> glows = new ArrayList<>();

    /** @param color ARGB, alpha = the glow's center alpha */
    public void add(Vec3 center, double radius, int color, int slices) {
        if (radius > 0 && ((color >>> 24) & 0xFF) > 0) glows.add(new Glow(center, radius, color, slices));
    }

    /** Draws everything collected and ends the glow batch. */
    public void draw(MultiBufferSource.BufferSource bufferSource, Matrix4f matrix, Vec3 camPos) {
        if (glows.isEmpty()) return;
        VertexConsumer buffer = bufferSource.getBuffer(GlowRenderType.GLOW);
        for (Glow glow : glows) {
            FireDraw.glow(matrix, buffer, glow.center(), glow.radius(), camPos, glow.color(), glow.slices());
        }
        bufferSource.endBatch(GlowRenderType.GLOW);
        glows.clear();
    }
}
