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
    @Nullable
    private static ShaderInstance depthPack;
    @Nullable
    private static ShaderInstance halo;
    @Nullable
    private static ShaderInstance smoke;
    @Nullable
    private static ShaderInstance film;
    @Nullable
    private static ShaderInstance puff;
    /** The terrain's fog this frame (see {@link #syncFog()}). */
    private static float fogStart;
    private static float fogEnd = 1.0f;
    private static final float[] FOG_COLOR = new float[4];

    /** For RenderTypes: the soft shader. */
    public static final RenderStateShard.ShaderStateShard SOFT_SHARD = new RenderStateShard.ShaderStateShard(ZaShaders::soft);
    /** For RenderTypes: the bubble film shader (per pixel). */
    public static final RenderStateShard.ShaderStateShard FILM_SHARD = new RenderStateShard.ShaderStateShard(ZaShaders::film);
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
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "depth_pack"),
                DefaultVertexFormat.POSITION_TEX), s -> depthPack = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "halo"),
                DefaultVertexFormat.POSITION_TEX_COLOR), s -> halo = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "smoke"),
                DefaultVertexFormat.POSITION), s -> smoke = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "film"),
                DefaultVertexFormat.POSITION_COLOR_NORMAL), s -> film = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ZoneArtifacts.MODID, "puff"),
                DefaultVertexFormat.POSITION_COLOR), s -> puff = s);
    }

    /**
     * The soft shader with soft edges: for what hangs in the air (gas puffs) and should melt where it
     * meets the ground. Things lying on a surface (puddles, cracks, the outline of a zone) use plain
     * {@link #soft()} — with soft edges they melted into the very ground they lie on.
     */
    public static ShaderInstance puff() {
        return puff != null ? puff : soft();
    }

    /** A soap bubble's film per pixel. Falls back to the soft shader until loaded. */
    public static ShaderInstance film() {
        return film != null ? film : soft();
    }

    /** The film shader if loaded, else null. */
    @Nullable
    public static ShaderInstance filmOrNull() {
        return film;
    }

    /** Smoke with depth, ray-marched ({@link Smoke}). Null until loaded. */
    @Nullable
    public static ShaderInstance smoke() {
        return smoke;
    }

    /** The glow of the air round the fireballs ({@link Halo}). Null until loaded. */
    @Nullable
    public static ShaderInstance halo() {
        return halo;
    }

    /** Packs the frame's depth into colour ({@link SceneDepth}). Null until loaded. */
    @Nullable
    public static ShaderInstance depthPack() {
        return depthPack;
    }

    /** Our shaders that read the frame (za_scene.glsl): the soft and glow ones, and those added later. */
    private static ShaderInstance[] sceneReaders() {
        return new ShaderInstance[]{soft, glow, halo, smoke, film, puff};
    }

    /**
     * What our shaders know of the frame this time round: the packed depth ({@code depthTexture}, 0 for
     * none), the world's inverse projection, the screen size, how far soft edges melt ({@code soft}, 0 =
     * off) and the dithering ({@code dither}, in 1/255 steps; 0 = off).
     */
    static void syncScene(int depthTexture, org.joml.Matrix4f invProjection, int width, int height, float soft, float dither) {
        for (ShaderInstance shader : sceneReaders()) {
            if (shader == null) continue;
            if (depthTexture != 0) shader.setSampler("ZaDepth", depthTexture);
            shader.safeGetUniform("ZaInvProj").set(invProjection);
            shader.safeGetUniform("ZaScreen").set((float) width, (float) height);
            // Soft edges only for what hangs in the air; puddles, cracks and glows on surfaces stay as they are.
            boolean airborne = shader == puff || shader == halo || shader == film;
            shader.safeGetUniform("ZaSoft").set(depthTexture != 0 && airborne ? soft : 0.0f);
            shader.safeGetUniform("ZaDither").set(dither);
        }
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
        for (ShaderInstance shader : new ShaderInstance[]{soft, glow, halo, film, puff}) {
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
