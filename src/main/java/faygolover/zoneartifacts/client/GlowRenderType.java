package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Vanilla's lightning look (position + colour, additive blending, unlit, back faces culled, depth
 * tested) — but <b>without writing depth</b>. For glows and other soft light: additive blending
 * doesn't care about drawing order, so they don't need depth, and writing it caused two problems —
 * layers of glow at the same depth flickered against each other (z-fighting ripples on the
 * Comet's halo), and a big, mostly transparent halo invisibly hid everything drawn after it
 * behind its whole disc.
 * <p>
 * Built from the render-state pieces directly (they're {@code protected} on RenderStateShard,
 * hence the subclass) with RenderType's public constructor, so it doesn't depend on the
 * visibility of {@code RenderType.create}.
 */
public final class GlowRenderType extends RenderType {

    public static final RenderType GLOW = new GlowRenderType();

    private GlowRenderType() {
        super("fl_zone_arts_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                () -> {
                    RENDERTYPE_LIGHTNING_SHADER.setupRenderState();
                    LIGHTNING_TRANSPARENCY.setupRenderState();
                    LEQUAL_DEPTH_TEST.setupRenderState();
                    CULL.setupRenderState();
                    WEATHER_TARGET.setupRenderState();
                    COLOR_WRITE.setupRenderState();
                },
                () -> {
                    COLOR_WRITE.clearRenderState();
                    WEATHER_TARGET.clearRenderState();
                    CULL.clearRenderState();
                    LEQUAL_DEPTH_TEST.clearRenderState();
                    LIGHTNING_TRANSPARENCY.clearRenderState();
                    RENDERTYPE_LIGHTNING_SHADER.clearRenderState();
                });
    }
}
