package faygolover.zoneartifacts.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.chem.Gas;
import faygolover.zoneartifacts.client.sumrak.SumrakClient;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Dusk and the Haze as smoke with depth (volumetric; HIGH quality and up, finer at ULTRA) in
 * place of their flat puffs: each zone's box, ray-marched per pixel in the smoke shader — the Dusk a
 * near-solid black mass with torn, curling edges, the Haze a thin grey mist lit by the world's light.
 * After the weather, like the puffs were.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Smoke {

    private static final double MAX_DISTANCE = 96.0;

    private record Volume(AABB box, float r, float g, float b, float density, float edge) {
    }

    private Smoke() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (LatePass.deferred(event)) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !ModClientConfig.volumetricSmoke()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || SceneDepth.texture() == 0) return;
        ShaderInstance shader = ZaShaders.smoke();
        if (shader == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        List<Volume> volumes = new ArrayList<>();

        List<AABB> dusks = SumrakClient.zones();
        List<Integer> density = SumrakClient.densities();
        for (int i = 0; i < dusks.size(); i++) {
            // Deep inside one sees only black (the Dusk's own darkness on screen): nothing to march through.
            // Only once the sight is fully gone — at the edge one still sees, and the smoke must be there.
            if (dusks.get(i).contains(cam) && SumrakClient.inside(partial) >= 0.97f) continue;
            int eff = i < density.size() ? density.get(i) : 3;
            volumes.add(new Volume(dusks.get(i), 0.012f, 0.012f, 0.016f, 2.4f + 0.25f * eff, 0.5f));
        }
        float skyDarken = level.getSkyDarken(partial);
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.DYMKA.equals(entry.typeId()) || !entry.visible() || !entry.enabled()) continue;
            AABB box = AnomalyGeometry.box(entry);
            int packedLight = LevelRenderer.getLightColor(level, BlockPos.containing(box.getCenter()));
            float light = faygolover.zoneartifacts.client.ZoneLight.at(level, box.getCenter(), partial);
            int eff = ModClientConfig.effective(entry.intensity());
            volumes.add(new Volume(box, 0.73f * light, 0.75f * light, 0.77f * light, 0.32f + 0.07f * eff, 0.7f));
        }
        volumes.removeIf(v -> v.box.getCenter().distanceToSqr(cam) > MAX_DISTANCE * MAX_DISTANCE);
        if (volumes.isEmpty()) return;
        // Far ones first, so near ones blend over them.
        volumes.sort(Comparator.comparingDouble((Volume v) -> v.box.getCenter().distanceToSqr(cam)).reversed());

        Matrix4f m = Gas.lateStagePose(event).last().pose();
        float time = ((level.getGameTime() % 72000L) + partial) / 20.0f;
        shader.safeGetUniform("ZaTime").set(time);
        shader.safeGetUniform("ZaOrigin").set((float) (cam.x % 4096.0), (float) (cam.y % 4096.0), (float) (cam.z % 4096.0));
        shader.safeGetUniform("ZaSteps").set(ModClientConfig.smokeSteps());
        RenderSystem.setShader(() -> shader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        // The box's far faces: they cover it whether the camera is outside or in.
        GL11.glCullFace(GL11.GL_FRONT);
        for (Volume v : volumes) {
            // A little bigger than the zone: the torn edges curl out past it.
            AABB box = v.box.inflate(v.edge * 2.5).move(-cam.x, -cam.y, -cam.z);
            shader.safeGetUniform("ZaBoxMin").set((float) box.minX, (float) box.minY, (float) box.minZ);
            shader.safeGetUniform("ZaBoxMax").set((float) box.maxX, (float) box.maxY, (float) box.maxZ);
            shader.safeGetUniform("ZaSmoke").set(v.r, v.g, v.b, v.density);
            shader.safeGetUniform("ZaEdge").set(v.edge);
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            box(buffer, m, box);
            BufferUploader.drawWithShader(buffer.end());
        }
        GL11.glCullFace(GL11.GL_BACK);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** The six faces, wound to face outwards. */
    private static void box(BufferBuilder b, Matrix4f m, AABB a) {
        float x0 = (float) a.minX, y0 = (float) a.minY, z0 = (float) a.minZ;
        float x1 = (float) a.maxX, y1 = (float) a.maxY, z1 = (float) a.maxZ;
        // -Y
        v(b, m, x0, y0, z0); v(b, m, x1, y0, z0); v(b, m, x1, y0, z1); v(b, m, x0, y0, z1);
        // +Y
        v(b, m, x0, y1, z0); v(b, m, x0, y1, z1); v(b, m, x1, y1, z1); v(b, m, x1, y1, z0);
        // -Z
        v(b, m, x0, y0, z0); v(b, m, x0, y1, z0); v(b, m, x1, y1, z0); v(b, m, x1, y0, z0);
        // +Z
        v(b, m, x0, y0, z1); v(b, m, x1, y0, z1); v(b, m, x1, y1, z1); v(b, m, x0, y1, z1);
        // -X
        v(b, m, x0, y0, z0); v(b, m, x0, y0, z1); v(b, m, x0, y1, z1); v(b, m, x0, y1, z0);
        // +X
        v(b, m, x1, y0, z0); v(b, m, x1, y1, z0); v(b, m, x1, y1, z1); v(b, m, x1, y0, z1);
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, float z) {
        b.vertex(m, x, y, z).endVertex();
    }
}
