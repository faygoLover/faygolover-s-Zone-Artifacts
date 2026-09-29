package faygolover.zoneartifacts.client.kisel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.client.ZoneLoopSound;
import faygolover.zoneartifacts.client.chem.Gas;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kisel's look and sound: a puddle of thick, dark green liquid with a bright green glow shimmering
 * on it (caustic-like, additive), glowing bubbles rising and bursting, and its green light falling
 * on the ground and walls around (faked: soft additive patches on the surfaces around it). When
 * something is in it, it seethes: far more bubbles, steam, a brighter glow. A quiet bubbling loop,
 * louder while it seethes (the hiss is the server's).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KiselClient {

    private static final double VISIBLE_RADIUS = 48.0;
    private static final int RINGS = 8;
    private static final int SEGMENTS = 32;
    private static final RandomSource RANDOM = RandomSource.create();

    private record LightSpot(Vec3 pos, Vec3 normal, double distance) {
    }

    private static final class State {
        SyncAnomaliesPacket.Entry entry;
        double surfaceY;
        float activity;
        float prevActivity;
        int nextScan;
        List<LightSpot> spots = List.of();
        @Nullable
        ZoneLoopSound loop;
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();

    private KiselClient() {
    }

    private static double puddleRadius(SyncAnomaliesPacket.Entry entry) {
        return entry.size() * 0.5 * 0.95;
    }

    private static double lightRadius(SyncAnomaliesPacket.Entry entry) {
        return 2.5 + entry.size() * 1.5;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            STATES.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Set<BlockPos> seen = new HashSet<>();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.KISEL.equals(entry.typeId())) continue;
            if (Vec3.atCenterOf(entry.pos()).distanceToSqr(cam) > VISIBLE_RADIUS * VISIBLE_RADIUS) continue;
            seen.add(entry.pos());
            State state = STATES.computeIfAbsent(entry.pos(), p -> new State());
            state.entry = entry;
            if (--state.nextScan <= 0) {
                scan(level, state);
                state.nextScan = 80 + RANDOM.nextInt(20);
            }
            state.prevActivity = state.activity;
            state.activity = entry.active() ? Math.min(1.0f, state.activity + 0.15f) : Math.max(0.0f, state.activity - 0.03f);

            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
            Vec3 c = zone.getCenter();
            double r = puddleRadius(entry);
            int eff = ModClientConfig.effective(entry.intensity());
            double rate = 0.05 * eff * Math.max(1.0, r * r * 4.0) * (1.0 + 4.0 * state.activity);
            int n = (int) rate + (RANDOM.nextDouble() < rate - (int) rate ? 1 : 0);
            for (int i = 0; i < n; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                double d = r * 0.85 * Math.sqrt(RANDOM.nextDouble());
                level.addParticle(ModParticles.KISEL_BUBBLE.get(), c.x + Math.cos(a) * d, state.surfaceY + 0.02, c.z + Math.sin(a) * d,
                        0.0, 0.004 + 0.01 * state.activity, 0.0);
            }
            if (state.activity > 0.2f && RANDOM.nextInt(5) == 0) {
                // Steam off the seething acid.
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                double d = r * 0.7 * Math.sqrt(RANDOM.nextDouble());
                Gas.add(new Gas.Puff(new Vec3(c.x + Math.cos(a) * d, state.surfaceY + 0.1, c.z + Math.sin(a) * d),
                        new Vec3(0.0, 0.03 + RANDOM.nextDouble() * 0.02, 0.0), 0.2, 0.7, now, 30 + RANDOM.nextInt(20),
                        0.25f * state.activity, 0x9CD86A, 0xC8F0A0, RANDOM.nextFloat() * 10f).drag(0.96).glow(0.6f));
            }
            if (state.loop == null || state.loop.isStopped()) {
                State s = state;
                BlockPos pos = entry.pos();
                state.loop = new ZoneLoopSound(ModSounds.KISEL_IDLE.get(), new Vec3(c.x, state.surfaceY + 0.2, c.z),
                        () -> STATES.get(pos) == s, () -> 0.45 + 0.45 * s.activity);
                mc.getSoundManager().play(state.loop);
            }
        }
        STATES.keySet().removeIf(pos -> !seen.contains(pos));
    }

    /** The surface, and where its light falls: rays out from just over it. */
    private static void scan(ClientLevel level, State state) {
        AABB zone = AnomalyGeometry.centeredAabb(state.entry.pos(), state.entry.size());
        Vec3 c = zone.getCenter();
        Double ground = Razlom.groundY(level, c.x, c.z, zone.maxY, zone.minY - 3.0);
        state.surfaceY = (ground != null ? ground : zone.minY) + 0.06;
        Vec3 from = new Vec3(c.x, state.surfaceY + 0.35, c.z);
        double reach = lightRadius(state.entry);
        List<LightSpot> spots = new ArrayList<>();
        int rays = 56;
        for (int i = 0; i < rays; i++) {
            // Spread over the lower half and a bit above the horizon (the light goes out and down).
            double y = -1.0 + 1.3 * (i + 0.5) / rays;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double phi = i * 2.399963;
            Vec3 dir = new Vec3(Math.cos(phi) * r, y, Math.sin(phi) * r);
            BlockHitResult hit = Razlom.clipBlocks(level, from, from.add(dir.scale(reach)));
            if (hit.getType() == HitResult.Type.MISS) continue;
            Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            spots.add(new LightSpot(hit.getLocation().add(n.scale(0.01)), n, hit.getLocation().distanceTo(from)));
        }
        state.spots = spots;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || STATES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick();
        float time = (mc.level.getGameTime() % 72000L) + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();

        // The liquid itself.
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        BufferBuilder body = Tesselator.getInstance().getBuilder();
        body.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (State state : STATES.values()) surface(body, m, state, time, Mth.lerp(partial, state.prevActivity, state.activity), false);
        BufferUploader.drawWithShader(body.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // Its glow, and its light on everything around.
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer glow = bufferSource.getBuffer(GlowRenderType.GLOW);
        for (State state : STATES.values()) {
            float act = Mth.lerp(partial, state.prevActivity, state.activity);
            surface(glow, m, state, time, act, true);
            float flicker = 1.0f + 0.08f * Mth.sin(time * 0.13f + state.entry.pos().hashCode()) + 0.05f * Mth.sin(time * 0.37f);
            float strength = (0.55f + 0.6f * act) * flicker;
            double reach = lightRadius(state.entry);
            for (LightSpot spot : state.spots) {
                double k = 1.0 - spot.distance() / reach;
                if (k <= 0.0) continue;
                int a = (int) (Mth.clamp(strength * k * k * 0.45f, 0.0f, 1.0f) * 255);
                if (a <= 2) continue;
                patch(glow, m, spot.pos(), spot.normal(), 0.7 + 0.5 * state.entry.size() * (1.0 - k * 0.5), 0x4CE01E, a);
            }
        }
        bufferSource.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    /** The puddle: dark liquid (body) or its shimmering glow (additive), with a soft torn rim. */
    private static void surface(VertexConsumer buffer, Matrix4f m, State state, float time, float activity, boolean glow) {
        AABB zone = AnomalyGeometry.centeredAabb(state.entry.pos(), state.entry.size());
        Vec3 c = zone.getCenter();
        double r0 = puddleRadius(state.entry);
        float s0 = (state.entry.pos().hashCode() & 0xFFFF) / 6553.6f;
        double y0 = state.surfaceY + (glow ? 0.006 : 0.0);
        Vec3[][] p = new Vec3[RINGS + 1][SEGMENTS + 1];
        int[][] col = new int[RINGS + 1][SEGMENTS + 1];
        int[][] al = new int[RINGS + 1][SEGMENTS + 1];
        for (int i = 0; i <= RINGS; i++) {
            double rho = i / (double) RINGS;
            for (int j = 0; j <= SEGMENTS; j++) {
                double phi = Math.PI * 2.0 * j / SEGMENTS;
                double edge = 0.86 + 0.09 * Math.sin(phi * 3.0 + s0) + 0.05 * Math.sin(phi * 7.0 - s0);
                double r = rho * r0 * edge;
                double x = c.x + Math.cos(phi) * r;
                double z = c.z + Math.sin(phi) * r;
                double wave = 0.006 * Math.sin(x * 4.0 + time * 0.09) * Math.sin(z * 4.3 - time * 0.07) * (1.0 + 2.0 * activity);
                p[i][j] = new Vec3(x, y0 + wave, z);
                float fade = (float) Math.min(1.0, (1.0 - rho) / 0.25);
                if (glow) {
                    double caustic = 0.5 + 0.5 * Math.sin(x * 3.1 + time * 0.07 + s0) * Math.sin(z * 3.4 - time * 0.055);
                    float a = (float) ((0.22 + 0.4 * activity) * (0.55 + 0.45 * caustic)) * fade;
                    col[i][j] = 0x6CFF2E;
                    al[i][j] = (int) (255 * Mth.clamp(a, 0.0f, 1.0f));
                } else {
                    col[i][j] = rho < 0.5 ? 0x2F8C16 : 0x1F5C0E;
                    al[i][j] = (int) (255 * (0.55 + 0.35 * fade));
                    if (i == RINGS) al[i][j] = 0;
                }
            }
        }
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                put(buffer, m, p[i][j], col[i][j], al[i][j]);
                put(buffer, m, p[i + 1][j], col[i + 1][j], al[i + 1][j]);
                put(buffer, m, p[i + 1][j + 1], col[i + 1][j + 1], al[i + 1][j + 1]);
                put(buffer, m, p[i][j + 1], col[i][j + 1], al[i][j + 1]);
            }
        }
    }

    /** A soft round patch of light lying on a surface. */
    private static void patch(VertexConsumer buffer, Matrix4f m, Vec3 c, Vec3 normal, double radius, int rgb, int alpha) {
        Vec3 helper = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 t = normal.cross(helper).normalize();
        Vec3 b = normal.cross(t).normalize();
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2.0 * i / n;
            double a1 = Math.PI * 2.0 * (i + 1) / n;
            Vec3 p0 = c.add(t.scale(Math.cos(a0) * radius)).add(b.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(t.scale(Math.cos(a1) * radius)).add(b.scale(Math.sin(a1) * radius));
            put(buffer, m, c, rgb, alpha);
            put(buffer, m, p0, rgb, 0);
            put(buffer, m, p1, rgb, 0);
            put(buffer, m, p1, rgb, 0);
        }
    }

    private static void put(VertexConsumer buffer, Matrix4f m, Vec3 p, int rgb, int alpha) {
        buffer.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Mth.clamp(alpha, 0, 255)).endVertex();
    }
}
