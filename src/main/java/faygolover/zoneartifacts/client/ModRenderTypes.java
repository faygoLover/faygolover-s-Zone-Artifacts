package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * A minimal custom {@link RenderType} for flat-shaded, untextured, translucent quads drawn in
 * world space (depth-tested against the world, not culled, no lighting). Vanilla doesn't ship a
 * ready-made one for this; this follows the same {@code RenderType.CompositeState} pattern
 * vanilla itself uses to build e.g. {@code RenderType.solid()}.
 * <p>
 * NOTE for whoever picks this up next: this is the one file in stage 1 that leans on
 * less-traveled rendering internals and could not be verified against a real compile in this
 * environment (no access to Mojang/Forge maven from here). If IntelliJ flags something in this
 * file after a Gradle sync, it's the first place to look — everything else in the mod is on much
 * more standard, well-trodden APIs.
 */
final class ModRenderTypes {

    static final RenderType ANOMALY_ZONE_FILL = RenderType.create(
            "fl_zone_arts_anomaly_zone_fill",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderType.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderType.NO_CULL)
                    .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderType.COLOR_WRITE)
                    .createCompositeState(false)
    );

    private ModRenderTypes() {
    }
}
