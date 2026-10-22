package faygolover.zoneartifacts.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

/** Whole-screen passes: one quad over a target with a shader, whatever the matrices were at the time. */
public final class ScreenPass {

    private ScreenPass() {
    }

    /** {@code target} sized like {@code main} (made or resized as needed), linear filtering. */
    public static TextureTarget fit(@Nullable TextureTarget target, RenderTarget main) {
        if (target == null) {
            target = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        }
        return target;
    }

    /** One quad over the whole of {@code target} with {@code shader} (depth test off; blending as set unless {@code blend} is false). */
    public static void fullscreen(RenderTarget target, ShaderInstance shader, boolean blend) {
        target.bindWrite(true);
        RenderSystem.disableDepthTest();
        if (!blend) RenderSystem.disableBlend();
        RenderSystem.setShader(() -> shader);
        quad(DefaultVertexFormat.POSITION_TEX, 0.0f);
    }

    /** A black quad over the whole bound target with this alpha (with the current shader and blending). */
    public static void black(float alpha) {
        quad(DefaultVertexFormat.POSITION_COLOR, alpha);
    }

    private static void quad(VertexFormat format, float alpha) {
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, format);
        float[][] corners = {{-1, -1, 0, 0}, {1, -1, 1, 0}, {1, 1, 1, 1}, {-1, 1, 0, 1}};
        for (float[] c : corners) {
            buffer.vertex(c[0], c[1], 0.0);
            if (format == DefaultVertexFormat.POSITION_TEX) buffer.uv(c[2], c[3]);
            else buffer.color(0.0f, 0.0f, 0.0f, alpha);
            buffer.endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, sorting);
    }
}
