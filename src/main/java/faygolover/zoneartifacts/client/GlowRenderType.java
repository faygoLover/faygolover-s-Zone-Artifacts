package faygolover.zoneartifacts.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import faygolover.zoneartifacts.client.sumrak.SumrakClient;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.client.render.ZaShaders;
import net.minecraft.client.renderer.RenderType;

/**
 * Vanilla's lightning look (position + colour, additive blending, unlit, depth
 * tested; our {@link ZaShaders#glow() glow shader}, so it fades in the fog) — but <b>without writing depth</b>. For glows and other soft light: additive blending
 * doesn't care about drawing order, so they don't need depth, and writing it caused two problems —
 * layers of glow at the same depth flickered against each other (z-fighting ripples on the
 * Comet's halo), and a big, mostly transparent halo invisibly hid everything drawn after it
 * behind its whole disc. No face culling either: everything in it faces the camera anyway, and in
 * the full pack something left the cull state turned round by the late stages — the Comets' fire
 * (drawn after the weather) vanished but for its core.
 * <p>
 * Built from the render-state pieces directly (they're {@code protected} on RenderStateShard,
 * hence the subclass) with RenderType's public constructor, so it doesn't depend on the
 * visibility of {@code RenderType.create}.
 */
public final class GlowRenderType extends RenderType {

    public static final RenderType GLOW = new GlowRenderType();

    /**
     * Vanilla's lightning (writes depth) on our {@link ZaShaders#glow() glow shader}, so arcs and
     * bolts sink into the fog too: Electra's discharges, the Tesla's loops.
     */
    public static final RenderType ARC = new RenderType("fl_zone_arts_arc", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            256, false, true,
            () -> {
                ZaShaders.GLOW_SHARD.setupRenderState();
                LIGHTNING_TRANSPARENCY.setupRenderState();
                LEQUAL_DEPTH_TEST.setupRenderState();
                CULL.setupRenderState();
                WEATHER_TARGET.setupRenderState();
                COLOR_DEPTH_WRITE.setupRenderState();
            },
            () -> {
                COLOR_DEPTH_WRITE.clearRenderState();
                WEATHER_TARGET.clearRenderState();
                CULL.clearRenderState();
                LEQUAL_DEPTH_TEST.clearRenderState();
                LIGHTNING_TRANSPARENCY.clearRenderState();
                ZaShaders.GLOW_SHARD.clearRenderState();
            }) {
    };

    private GlowRenderType() {
        super("fl_zone_arts_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                () -> {
                    ZaShaders.GLOW_SHARD.setupRenderState();
                    LIGHTNING_TRANSPARENCY.setupRenderState();
                    LEQUAL_DEPTH_TEST.setupRenderState();
                    NO_CULL.setupRenderState();
                    WEATHER_TARGET.setupRenderState();
                    COLOR_WRITE.setupRenderState();
                    // Glows ignore fog; from inside the Dusk they must fade like everything else.
                    float k = SumrakClient.glowLight();
                    if (k < 0.999f) RenderSystem.setShaderColor(k, k, k, 1.0f);
                },
                () -> {
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    COLOR_WRITE.clearRenderState();
                    WEATHER_TARGET.clearRenderState();
                    NO_CULL.clearRenderState();
                    LEQUAL_DEPTH_TEST.clearRenderState();
                    LIGHTNING_TRANSPARENCY.clearRenderState();
                    ZaShaders.GLOW_SHARD.clearRenderState();
                });
    }
}
