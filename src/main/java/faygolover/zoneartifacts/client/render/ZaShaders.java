package faygolover.zoneartifacts.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * The anomalies' own two shaders — vanilla's position_color and lightning, but both sink into the
 * world's fog (the Haze closing in, the far plane): {@link #soft()} for alpha-blended shapes (they
 * fade to the fog colour), {@link #glow()} for additive glows and arcs (they fade out).
 * <p>
 * A mod's own shader is blanked by Oculus while a shader pack draws the world, so with a pack they
 * are only ever used in the {@link LatePass} (after the pack has finished its frame). Until they are
 * loaded, vanilla's stand in.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ZaShaders {

    @Nullable
    private static ShaderInstance soft;
    @Nullable
    private static ShaderInstance glow;
    @Nullable
    private static ShaderInstance handMask;
    @Nullable
    private static ShaderInstance handOver;
    @Nullable
    private static ShaderInstance handDiff;
    @Nullable
    private static ShaderInstance ghost;
    @Nullable
    private static ShaderInstance screenFog;
    @Nullable
    private static ShaderInstance distort;
    @Nullable
    private static ShaderInstance anomalyLight;
    /** The terrain's fog this frame (see {@link #syncFog()}). */
    private static float fogStart;
    private static float fogEnd = 1.0f;
    private static final float[] FOG_COLOR = new float[4];

    /** For RenderTypes: the soft shader. */
    public static final RenderStateShard.ShaderStateShard SOFT_SHARD = new RenderStateShard.ShaderStateShard(ZaShaders::soft);
    /** For RenderTypes: the glow shader. */
    public static final RenderStateShard.ShaderStateShard GLOW_SHARD = new RenderStateShard.ShaderStateShard(ZaShaders::glow);

    private ZaShaders() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "soft"),
                DefaultVertexFormat.POSITION_COLOR), s -> soft = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "glow"),
                DefaultVertexFormat.POSITION_COLOR), s -> glow = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "hand_mask"),
                DefaultVertexFormat.POSITION_TEX), s -> handMask = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "hand_over"),
                DefaultVertexFormat.POSITION_TEX), s -> handOver = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "hand_diff"),
                DefaultVertexFormat.POSITION_TEX), s -> handDiff = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "ghost"),
                DefaultVertexFormat.POSITION_TEX), s -> ghost = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "screen_fog"),
                DefaultVertexFormat.POSITION_TEX), s -> screenFog = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "distort"),
                DefaultVertexFormat.POSITION_TEX_COLOR), s -> distort = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "anomaly_light"),
                DefaultVertexFormat.POSITION_TEX), s -> anomalyLight = s);
    }

    /** The anomalies' light on the world (screen pass). Null until loaded. */
    @Nullable
    public static ShaderInstance anomalyLight() {
        return anomalyLight;
    }

    /** Refraction per pixel (the air distortion at MEDIUM quality and up). Null until loaded. */
    @Nullable
    public static ShaderInstance distort() {
        return distort;
    }

    /** Over a shader pack's frame: the Haze's / the Dusk's fog from the depth. Null until loaded. */
    @Nullable
    public static ShaderInstance screenFog() {
        return screenFog;
    }

    public static float fogStart() {
        return fogStart;
    }

    public static float fogEnd() {
        return fogEnd;
    }

    public static float[] fogColor() {
        return FOG_COLOR;
    }

    /** Screen pass (the psi sight): the hand's fading after-image. Null until loaded. */
    @Nullable
    public static ShaderInstance ghost() {
        return ghost;
    }

    /** Screen pass (the psi sight, no shader pack): where the hand is, from what changed after the world. Null until loaded. */
    @Nullable
    public static ShaderInstance handDiff() {
        return handDiff;
    }

    /** Screen pass (the psi sight): where the hand is, from the depth. Null until loaded. */
    @Nullable
    public static ShaderInstance handMask() {
        return handMask;
    }

    /** Screen pass (the psi sight): the hand (or its after-image) drawn over, through a mask. Null until loaded. */
    @Nullable
    public static ShaderInstance handOver() {
        return handOver;
    }

    /**
     * The world's fog, as it was set for the terrain this frame, into our two shaders. They don't take
     * the fog from whatever is set at the moment of drawing: by the late stages (after the weather)
     * some mods leave odd fog values, and the Comets' fire faded out except up close.
     */
    public static void syncFog() {
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        float[] color = RenderSystem.getShaderFogColor();
        fogStart = start;
        fogEnd = end;
        System.arraycopy(color, 0, FOG_COLOR, 0, 4);
        for (ShaderInstance shader : new ShaderInstance[]{soft, glow}) {
            if (shader == null) continue;
            shader.safeGetUniform("ZaFogStart").set(start);
            shader.safeGetUniform("ZaFogEnd").set(end);
            shader.safeGetUniform("ZaFogColor").set(color[0], color[1], color[2], color[3]);
        }
    }

    /** Position + colour, alpha-blended, fogged towards the fog colour. */
    public static ShaderInstance soft() {
        return soft != null ? soft : GameRenderer.getPositionColorShader();
    }

    /** Position + colour, for additive light: fades out in the fog. */
    public static ShaderInstance glow() {
        return glow != null ? glow : GameRenderer.getRendertypeLightningShader();
    }
}
