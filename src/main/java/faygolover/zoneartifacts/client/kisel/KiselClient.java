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
import faygolover.zoneartifacts.anomaly.Kisel;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kisel's look and sound: a puddle of thick, dark green liquid. Its glow isn't spread over the whole
 * of it but sits in a few seething spots (more of them the bigger it is): there the liquid is lighter,
 * glowing bubbles rise and burst, and a soft green light falls on the ground and walls close by
 * (faked: additive patches on the surfaces). When something is in it, it seethes: it builds out in tongues,
 * new seething spots open up, the old ones boil harder and glow brighter, steam rises. A quiet
 * bubbling loop, louder while it seethes; of several Kisels close together only the nearest few
 * are heard, the nearest the loudest, each at its own pitch (the hiss is the server's).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KiselClient {

    private static final double VISIBLE_RADIUS = 48.0;
    private static final int RINGS = 10;
    private static final int SEGMENTS = 36;
    /** How many Kisels are heard at once, and how loud each by closeness rank. */
    private static final float[] RANK_GAIN = {1.0f, 0.4f, 0.2f};
    private static final int GLOW_RGB = 0x4CD01E;
    private static final RandomSource RANDOM = RandomSource.create();

    private record LightSpot(Vec3 pos, Vec3 normal, double distance) {
    }

    /** A seething spot: offset from the centre, its radius, and whether it only opens while seething. */
    private record Hot(double dx, double dz, double radius, boolean extra, List<LightSpot> spots) {
    }

    private static final class State {
        SyncAnomaliesPacket.Entry entry;
        double surfaceY;
        float activity;
        float prevActivity;
        int nextScan;
        float scannedSize = -1.0f;
        List<Hot> hots = List.of();
        int rank;
        @Nullable
        ZoneLoopSound loop;
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();

    private KiselClient() {
    }

    /** The puddle at rest reaches just past where stepping wakes it. */
    private static double restRadius(SyncAnomaliesPacket.Entry entry) {
        return Kisel.reactRadius(entry.size()) * 1.04;
    }

    /** How much of a spot shows at this activity (the extra ones open up as it seethes). */
    private static float weight(Hot hot, float activity) {
        if (!hot.extra()) return 1.0f;
        float t = Mth.clamp((activity - 0.15f) / 0.6f, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
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
            if (--state.nextScan <= 0 || state.scannedSize != entry.size()) {
                scan(level, state);
                state.nextScan = 80 + RANDOM.nextInt(20);
            }
            state.prevActivity = state.activity;
            state.activity = entry.active() ? Math.min(1.0f, state.activity + 0.08f) : Math.max(0.0f, state.activity - 0.02f);

            AABB zone = AnomalyGeometry.box(entry);
            Vec3 c = zone.getCenter();
            float act = state.activity;
            int eff = ModClientConfig.effective(entry.intensity());
            for (Hot hot : state.hots) {
                float w = weight(hot, act);
                if (w <= 0.01f) continue;
                double rate = (0.07 + 0.3 * act) * (eff / 3.0) * w * Math.max(0.6, hot.radius() / 0.3);
                int n = (int) rate + (RANDOM.nextDouble() < rate - (int) rate ? 1 : 0);
                for (int i = 0; i < n; i++) {
                    double bx = c.x + hot.dx() + RANDOM.nextGaussian() * hot.radius() * 0.35;
                    double bz = c.z + hot.dz() + RANDOM.nextGaussian() * hot.radius() * 0.35;
                    faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, ModParticles.KISEL_BUBBLE.get(), bx, state.surfaceY + 0.02, bz,
                            0.0, 0.004 + 0.008 * act, 0.0);
                }
                if (act > 0.25f && RANDOM.nextInt(14) == 0) {
                    // Steam off the seething spot.
                    Gas.add(new Gas.Puff(new Vec3(c.x + hot.dx() + RANDOM.nextGaussian() * 0.15, state.surfaceY + 0.1,
                            c.z + hot.dz() + RANDOM.nextGaussian() * 0.15),
                            new Vec3(0.0, 0.025 + RANDOM.nextDouble() * 0.02, 0.0), 0.15, 0.55, now, 30 + RANDOM.nextInt(20),
                            0.2f * act * w, 0x9CD86A, 0xC8F0A0, RANDOM.nextFloat() * 10f).drag(0.96).glow(0.4f));
                }
            }
        }
        STATES.keySet().removeIf(pos -> !seen.contains(pos));

        // Sound: only the nearest few, the nearest the loudest (so it can still be found by ear).
        List<State> byDistance = new ArrayList<>(STATES.values());
        byDistance.sort(Comparator.comparingDouble(st -> Vec3.atCenterOf(st.entry.pos()).distanceToSqr(cam)));
        for (int i = 0; i < byDistance.size(); i++) {
            State state = byDistance.get(i);
            state.rank = i;
            if (i >= RANK_GAIN.length || (state.loop != null && !state.loop.isStopped())) continue;
            BlockPos pos = state.entry.pos();
            AABB zone = AnomalyGeometry.centeredAabb(pos, state.entry.size());
            Vec3 c = zone.getCenter();
            float pitch = 0.85f + ((pos.hashCode() >>> 3) % 31) / 100.0f;
            state.loop = new ZoneLoopSound(ModSounds.KISEL_IDLE.get(), new Vec3(c.x, state.surfaceY + 0.2, c.z),
                    () -> STATES.get(pos) == state && state.rank < RANK_GAIN.length,
                    () -> (0.35 + 0.4 * state.activity) * RANK_GAIN[Math.min(state.rank, RANK_GAIN.length - 1)]).pitch(pitch);
            mc.getSoundManager().play(state.loop);
        }
    }

    /** The surface; where the seething spots are; and where each one's light falls. */
    private static void scan(ClientLevel level, State state) {
        AABB zone = AnomalyGeometry.box(state.entry);
        Vec3 c = zone.getCenter();
        Double ground = Razlom.groundY(level, c.x, c.z, zone.maxY, zone.minY - 3.0);
        state.surfaceY = (ground != null ? ground : zone.minY) + 0.06;
        state.scannedSize = state.entry.size();

        double size = state.entry.size();
        double rest = restRadius(state.entry);
        Kisel.Tongue[] tongues = Kisel.tongues(state.entry.pos().asLong());
        int base = Mth.clamp((int) Math.round(0.5 + 0.55 * size * size), 1, 10);
        int extra = Math.max(1, Math.round(base * 0.7f));
        RandomSource shape = RandomSource.create(state.entry.pos().asLong() * 0x9E3779B97F4A7C15L);
        List<double[]> placed = new ArrayList<>();
        List<Hot> hots = new ArrayList<>();
        for (int i = 0; i < base + extra; i++) {
            boolean isExtra = i >= base;
            double hr = Math.min(0.45, 0.2 + 0.07 * size) * (0.8 + 0.4 * shape.nextDouble());
            double dx = 0.0, dz = 0.0;
            for (int attempt = 0; attempt < 8; attempt++) {
                double a = shape.nextDouble() * Math.PI * 2.0;
                double d = rest * 0.62 * Math.sqrt(shape.nextDouble());
                if (isExtra) {
                    // New spots open up out in the tongues it builds while seething.
                    Kisel.Tongue t = tongues[(i - base) % tongues.length];
                    a = t.angle() + (shape.nextDouble() - 0.5) * t.width();
                    d = rest + Kisel.reactRadius(size) * t.reach() * (0.35 + 0.35 * shape.nextDouble());
                }
                if (base == 1 && i == 0) d *= 0.4;
                dx = Math.cos(a) * d;
                dz = Math.sin(a) * d;
                boolean clear = true;
                for (double[] o : placed) {
                    if (Math.hypot(o[0] - dx, o[1] - dz) < (o[2] + hr) * 0.8) {
                        clear = false;
                        break;
                    }
                }
                if (clear) break;
            }
            placed.add(new double[]{dx, dz, hr});
            Vec3 from = new Vec3(c.x + dx, state.surfaceY + 0.25, c.z + dz);
            double reach = Math.min(3.0, 1.2 + 0.3 * size);
            List<LightSpot> spots = new ArrayList<>();
            int rays = 14;
            for (int k = 0; k < rays; k++) {
                // Out and down, a little above the horizon.
                double y = -1.0 + 1.25 * (k + 0.5) / rays;
                double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
                double phi = k * 2.399963 + i;
                Vec3 dir = new Vec3(Math.cos(phi) * r, y, Math.sin(phi) * r);
                BlockHitResult hit = Razlom.clipBlocks(level, from, from.add(dir.scale(reach)));
                if (hit.getType() == HitResult.Type.MISS) continue;
                Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                spots.add(new LightSpot(hit.getLocation().add(n.scale(0.01)), n, hit.getLocation().distanceTo(from)));
            }
            hots.add(new Hot(dx, dz, hr, isExtra, spots));
        }
        state.hots = hots;
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

        // The liquid itself (lighter where it seethes).
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        BufferBuilder body = Tesselator.getInstance().getBuilder();
        body.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (State state : STATES.values()) {
            if (state.entry.visible()) surface(body, m, state, time, Mth.lerp(partial, state.prevActivity, state.activity));
        }
        BufferUploader.drawWithShader(body.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // The glow of its seething spots, and their light on what's close by.
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer glow = bufferSource.getBuffer(GlowRenderType.GLOW);
        for (State state : STATES.values()) {
            if (!state.entry.visible()) continue;
            float act = Mth.lerp(partial, state.prevActivity, state.activity);
            AABB zone = AnomalyGeometry.box(state.entry);
            Vec3 c = zone.getCenter();
            float strength = 0.16f + 0.24f * act;
            double reach = Math.min(3.0, 1.2 + 0.3 * state.entry.size());
            int i = 0;
            for (Hot hot : state.hots) {
                float w = weight(hot, act);
                i++;
                if (w <= 0.01f) continue;
                // Slow, gentle breathing of each spot on its own (no flicker).
                float breath = 0.9f + 0.1f * Mth.sin(time * 0.02f + i * 1.7f);
                Vec3 at = new Vec3(c.x + hot.dx(), state.surfaceY + 0.008, c.z + hot.dz());
                blob(glow, m, at, hot.radius() * (0.9 + 0.25 * act), GLOW_RGB, (int) (255 * strength * w * breath), i, time);
                blob(glow, m, at.add(0.0, 0.002, 0.0), hot.radius() * 0.45, 0x8CFF50, (int) (255 * strength * 0.7f * w * breath), i + 7, time);
                for (LightSpot spot : hot.spots()) {
                    double k = 1.0 - spot.distance() / reach;
                    if (k <= 0.0) continue;
                    int a = (int) (Mth.clamp(strength * w * (float) (k * k) * 0.3f, 0.0f, 1.0f) * 255);
                    if (a <= 2) continue;
                    patch(glow, m, spot.pos(), spot.normal(), 0.35 + 0.35 * (1.0 - k), GLOW_RGB, a);
                }
            }
        }
        bufferSource.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    /** The puddle: dark liquid with a soft torn rim, lighter around its seething spots. */
    private static void surface(VertexConsumer buffer, Matrix4f m, State state, float time, float activity) {
        AABB zone = AnomalyGeometry.box(state.entry);
        Vec3 c = zone.getCenter();
        long seed = state.entry.pos().asLong();
        Kisel.Tongue[] tongues = Kisel.tongues(seed);
        double y0 = state.surfaceY;
        Vec3[][] p = new Vec3[RINGS + 1][SEGMENTS + 1];
        int[][] col = new int[RINGS + 1][SEGMENTS + 1];
        int[][] al = new int[RINGS + 1][SEGMENTS + 1];
        for (int i = 0; i <= RINGS; i++) {
            double rho = i / (double) RINGS;
            for (int j = 0; j <= SEGMENTS; j++) {
                double phi = Math.PI * 2.0 * j / SEGMENTS;
                // Its edge: just past the react circle at rest, tongues building out while it seethes.
                double r = rho * Kisel.outline(state.entry.size(), seed, tongues, phi, activity);
                double x = c.x + Math.cos(phi) * r;
                double z = c.z + Math.sin(phi) * r;
                double heat = 0.0;
                for (Hot hot : state.hots) {
                    double d = Math.hypot(x - c.x - hot.dx(), z - c.z - hot.dz()) / (hot.radius() * 1.3);
                    if (d < 1.0) heat = Math.max(heat, (1.0 - d * d) * weight(hot, activity));
                }
                double wave = 0.005 * Math.sin(x * 4.0 + time * 0.09) * Math.sin(z * 4.3 - time * 0.07) * (1.0 + 2.0 * heat * activity);
                p[i][j] = new Vec3(x, y0 + wave, z);
                float fade = (float) Math.min(1.0, (1.0 - rho) / 0.25);
                col[i][j] = mix(rho < 0.5 ? 0x2A7A14 : 0x1D540D, 0x48A824, (float) (heat * (0.55 + 0.3 * activity)));
                al[i][j] = i == RINGS ? 0 : (int) (255 * (0.55 + 0.35 * fade));
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

    /** A soft, slightly lumpy glowing blob lying flat on the surface. */
    private static void blob(VertexConsumer buffer, Matrix4f m, Vec3 c, double radius, int rgb, int alpha, int seed, float time) {
        if (alpha <= 2) return;
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2.0 * i / n;
            double a1 = Math.PI * 2.0 * (i + 1) / n;
            double r0 = radius * (0.85 + 0.15 * Math.sin(a0 * 3.0 + seed + time * 0.01));
            double r1 = radius * (0.85 + 0.15 * Math.sin(a1 * 3.0 + seed + time * 0.01));
            put(buffer, m, c, rgb, alpha);
            put(buffer, m, c.add(Math.cos(a0) * r0, 0.0, Math.sin(a0) * r0), rgb, 0);
            put(buffer, m, c.add(Math.cos(a1) * r1, 0.0, Math.sin(a1) * r1), rgb, 0);
            put(buffer, m, c.add(Math.cos(a1) * r1, 0.0, Math.sin(a1) * r1), rgb, 0);
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

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    private static void put(VertexConsumer buffer, Matrix4f m, Vec3 p, int rgb, int alpha) {
        buffer.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Mth.clamp(alpha, 0, 255)).endVertex();
    }
}
