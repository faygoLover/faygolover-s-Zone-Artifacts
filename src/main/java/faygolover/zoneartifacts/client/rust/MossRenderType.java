package faygolover.zoneartifacts.client.rust;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Rust's moss layers: drawn like cutout blocks (the block shader — world light from the lightmap,
 * the face's shade baked into the colour, no per-camera lighting), with the layer's own texture and
 * both sides. The block shader lets the moss be built once into a vertex buffer ({@link RustClient})
 * instead of every frame: the entity shaders light by normals in camera space, which a buffer built
 * once can't have.
 */
final class MossRenderType extends RenderType {

    private MossRenderType(String name, RenderStateShard.TextureStateShard texture) {
        super(name, DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 262144, false, false,
                () -> {
                    RENDERTYPE_CUTOUT_SHADER.setupRenderState();
                    texture.setupRenderState();
                    NO_TRANSPARENCY.setupRenderState();
                    LEQUAL_DEPTH_TEST.setupRenderState();
                    NO_CULL.setupRenderState();
                    LIGHTMAP.setupRenderState();
                    COLOR_DEPTH_WRITE.setupRenderState();
                },
                () -> {
                    COLOR_DEPTH_WRITE.clearRenderState();
                    LIGHTMAP.clearRenderState();
                    NO_CULL.clearRenderState();
                    LEQUAL_DEPTH_TEST.clearRenderState();
                    NO_TRANSPARENCY.clearRenderState();
                    texture.clearRenderState();
                    RENDERTYPE_CUTOUT_SHADER.clearRenderState();
                });
    }

    static RenderType of(ResourceLocation texture) {
        return new MossRenderType("fl_zone_arts_moss_" + texture.getPath().replace('/', '_').replace(".png", ""),
                new RenderStateShard.TextureStateShard(texture, false, false));
    }
}
