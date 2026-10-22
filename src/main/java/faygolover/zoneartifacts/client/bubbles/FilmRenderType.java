package faygolover.zoneartifacts.client.bubbles;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.client.render.ZaShaders;
import net.minecraft.client.renderer.RenderType;

/**
 * A soap film: position + colour, ordinary alpha blending (like the Amoeba's jelly), both sides,
 * depth tested but not written — the far side of the bubble shows through the near one, and the
 * bubble never hides what's drawn after it.
 */
final class FilmRenderType extends RenderType {

    static final RenderType FILM = new FilmRenderType();

    /** The same, worked out per pixel in the film shader: position, colour (how lit / how bright) and the sphere's normal. */
    static final RenderType FILM_PIXEL = new RenderType("fl_zone_arts_film_pixel", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.QUADS, 65536, false, true,
            () -> {
                ZaShaders.FILM_SHARD.setupRenderState();
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
                ZaShaders.FILM_SHARD.clearRenderState();
            }) {
    };

    private FilmRenderType() {
        super("fl_zone_arts_film", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, true,
                () -> {
                    ZaShaders.SOFT_SHARD.setupRenderState();
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
                    ZaShaders.SOFT_SHARD.clearRenderState();
                });
    }
}
