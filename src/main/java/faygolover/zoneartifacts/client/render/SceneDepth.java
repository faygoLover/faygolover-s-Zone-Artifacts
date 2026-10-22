package faygolover.zoneartifacts.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import faygolover.zoneartifacts.config.ModClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

/**
 * The frame's depth as our shaders can read it while they draw into the frame: packed into a colour
 * texture once a frame (a texture can't be read while its own framebuffer is drawn to), with the
 * world's projection to turn it back into distances. Soft edges and the smoke stopping at walls use
 * it. Taken after the entities (without a shader pack) or at the start of the late pass (with one).
 */
public final class SceneDepth {

    /** How far before the world behind them soft things have melted away, blocks. */
    private static final float SOFT_RANGE = 0.7f;

    @Nullable
    private static TextureTarget packed;
    private static boolean ready;

    private SceneDepth() {
    }

    /** Packs the main target's depth now and hands it (with {@code projection}) to our shaders. */
    static void capture(Matrix4f projection) {
        ready = false;
        ShaderInstance pack = ZaShaders.depthPack();
        if (pack == null) return;
        boolean soft = ModClientConfig.softEdges();
        boolean smoke = ModClientConfig.volumetricSmoke();
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (soft || smoke) {
            if (packed == null) {
                packed = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
                packed.setFilterMode(GL11.GL_NEAREST);
            } else if (packed.width != main.width || packed.height != main.height) {
                packed.resize(main.width, main.height, Minecraft.ON_OSX);
                packed.setFilterMode(GL11.GL_NEAREST);
            }
            pack.setSampler("DepthSampler", main.getDepthTextureId());
            ScreenPass.fullscreen(packed, pack, false);
            main.bindWrite(true);
            ready = true;
        }
        ZaShaders.syncScene(ready ? packed.getColorTextureId() : 0, new Matrix4f(projection).invert(), main.width, main.height,
                ready && soft ? SOFT_RANGE : 0.0f, ModClientConfig.dithering() ? 1.5f : 0.0f);
    }

    /** The packed depth texture this frame, or 0. */
    public static int texture() {
        return ready && packed != null ? packed.getColorTextureId() : 0;
    }
}
