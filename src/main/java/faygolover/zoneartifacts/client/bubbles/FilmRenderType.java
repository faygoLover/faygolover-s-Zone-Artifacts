package faygolover.zoneartifacts.client.bubbles;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * A soap film: position + colour, ordinary alpha blending (like the Amoeba's jelly), both sides,
 * depth tested but not written — the far side of the bubble shows through the near one, and the
 * bubble never hides what's drawn after it.
 */
final class FilmRenderType extends RenderType {

    static final RenderType FILM = new FilmRenderType();

    private FilmRenderType() {
        super("fl_zone_arts_film", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, true,
                () -> {
                    POSITION_COLOR_SHADER.setupRenderState();
                    TRANSLUCENT_TRANSPARENCY.setupRenderState();
                    LEQUAL_DEPTH_TEST.setupRenderState();
                    NO_CULL.setupRenderState();
                    COLOR_WRITE.setupRenderState();
                },
                () -> {
                    COLOR_WRITE.clearRenderState();
                    NO_CULL.clearRenderState();
                    LEQUAL_DEPTH_TEST.clearRenderState();
                    TRANSLUCENT_TRANSPARENCY.clearRenderState();
                    POSITION_COLOR_SHADER.clearRenderState();
                });
    }
}
