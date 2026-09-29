package faygolover.zoneartifacts.client.amoeba;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.Amoeba;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.gravity.GoreClient;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.AmoebaEventPacket;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Amoeba's look: a translucent jelly (plain alpha blending, lit by the world) — a flat,
 * slowly rippling puddle at rest; when it wakes it draws together into a quivering, pulsing dome,
 * whips pseudopods out of it (tapering, writhing, each leaving a burn where it strikes the ground),
 * then slumps back into a puddle, paler. After the cooldown its colour seeps back.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AmoebaClient {

    private static final double VISIBLE_RADIUS = 48.0;
    private static final float FADE_IN_TICKS = 60.0f;
    private static final int RINGS = 10;
    private static final int SEGMENTS = 28;
    private static final RandomSource RANDOM = RandomSource.create();

    private static final int GEL = 0x6F8A3A;
    private static final int GEL_LIGHT = 0xA3B862;
    private static final int GEL_PINK = 0xC8938C;
    private static final int GEL_PALE = 0x9A9C88;

    private record Lash(Vec3 origin, Vec3 tip, long start, float seed) {
    }

    private static final class State {
        SyncAnomaliesPacket.Entry entry;
        long gatherTick = -1;
        boolean resting;
        long readySince = -1_000_000L;
        long restSince = -1_000_000L;
        final List<Lash> lashes = new ArrayList<>();
        Double groundY;
        int nextScan;
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();

    private AmoebaClient() {
    }

    private static int attackTicks() {
        try {
            return (int) Math.round(ModCommonConfig.AMOEBA_ATTACK_SECONDS.get() * 20.0);
        } catch (IllegalStateException notLoaded) {
            return 60;
        }
    }

    public static void onEvent(BlockPos pos, byte event, Vec3 a, Vec3 b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State state = STATES.get(pos);
        if (state == null) return;
        long now = mc.level.getGameTime();
        switch (event) {
            case AmoebaEventPacket.GATHER -> {
                state.gatherTick = now;
                state.lashes.clear();
            }
            case AmoebaEventPacket.LASH -> state.lashes.add(new Lash(a, b, now, RANDOM.nextFloat() * 10.0f));
            case AmoebaEventPacket.SETTLE -> {
                state.gatherTick = -1;
                state.lashes.clear();
            }
            default -> {
            }
        }
    }

    // ==== tick ===================================================================================

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
            if (!AnomalyTypeIds.AMOEBA.equals(entry.typeId())) continue;
            if (Vec3.atCenterOf(entry.pos()).distanceToSqr(cam) > VISIBLE_RADIUS * VISIBLE_RADIUS) continue;
            seen.add(entry.pos());
            State state = STATES.computeIfAbsent(entry.pos(), p -> new State());
            boolean fresh = state.entry == null;
            state.entry = entry;
            if (entry.onCooldown()) {
                if (!state.resting) state.restSince = fresh ? now - 1_000_000L : now;
                state.resting = true;
            } else if (state.resting || fresh) {
                state.readySince = fresh ? now - 1_000_000L : now;
                state.resting = false;
            }
            if (entry.active() && state.gatherTick < 0) state.gatherTick = now - Amoeba.GATHER_TICKS; // came in mid-attack
            if (!entry.active() && state.gatherTick >= 0 && now - state.gatherTick > Amoeba.GATHER_TICKS + attackTicks() + Amoeba.SETTLE_TICKS + 10) {
                state.gatherTick = -1;
            }
            if (--state.nextScan <= 0) {
                AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
                Vec3 c = zone.getCenter();
                state.groundY = Razlom.groundY(level, c.x, c.z, zone.maxY, zone.minY - 3.0);
                state.nextScan = 60 + RANDOM.nextInt(20);
            }
            tickLashes(level, state, now);
            if (state.gatherTick >= 0 && RANDOM.nextInt(12) == 0) {
                // Acid dripping off the dome.
                Vec3 base = base(state);
                double dome = Amoeba.domeRadius(entry.size());
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                level.addParticle(ModParticles.CHEM_DROP.get(), base.x + Math.cos(a) * dome * 0.7, base.y + dome * 0.5,
                        base.z + Math.sin(a) * dome * 0.7, Math.cos(a) * 0.03, 0.05, Math.sin(a) * 0.03);
            }
        }
        STATES.keySet().removeIf(pos -> !seen.contains(pos));
    }

    private static void tickLashes(ClientLevel level, State state, long now) {
        for (Iterator<Lash> it = state.lashes.iterator(); it.hasNext(); ) {
            Lash lash = it.next();
            long age = now - lash.start();
            if (age > Amoeba.LASH_TICKS) {
                it.remove();
                continue;
            }
            if (age == Amoeba.LASH_EXTEND) {
                // It strikes: a splash, and a burn if it hit the ground.
                Vec3 tip = lash.tip();
                for (int i = 0; i < 4; i++) {
                    level.addParticle(ModParticles.CHEM_DROP.get(), tip.x, tip.y + 0.05, tip.z,
                            RANDOM.nextGaussian() * 0.06, 0.08 + RANDOM.nextDouble() * 0.06, RANDOM.nextGaussian() * 0.06);
                }
                GoreClient.addStain(level, tip.add(0.0, 0.4, 0.0), new Vec3(0, -1, 0), 1.0,
                        0.2 + RANDOM.nextDouble() * 0.2, 0x7A8A3A, 0x3A4418, 130, 700);
            }
        }
    }

    private static Vec3 base(State state) {
        AABB zone = AnomalyGeometry.centeredAabb(state.entry.pos(), state.entry.size());
        Vec3 c = zone.getCenter();
        return new Vec3(c.x, state.groundY != null ? state.groundY : zone.minY, c.z);
    }

    /** 0 = puddle, 1 = dome. */
    private static float gather(State state, long now, float partial) {
        if (state.gatherTick < 0) return 0.0f;
        float pt = now - state.gatherTick + partial;
        int attackEnd = Amoeba.GATHER_TICKS + attackTicks();
        float g;
        if (pt < Amoeba.GATHER_TICKS) g = pt / Amoeba.GATHER_TICKS;
        else if (pt < attackEnd) g = 1.0f;
        else g = 1.0f - (pt - attackEnd) / Amoeba.SETTLE_TICKS;
        g = Mth.clamp(g, 0.0f, 1.0f);
        return g * g * (3.0f - 2.0f * g);
    }

    /** 1 = full colour; 0.35 when spent (it pales as it slumps, and regains colour after the cooldown). */
    private static float vivid(State state, long now, float partial) {
        if (state.resting) {
            float t = Mth.clamp((now - state.restSince + partial) / 20.0f, 0.0f, 1.0f);
            return 1.0f - 0.65f * t;
        }
        float t = Mth.clamp((now - state.readySince + partial) / FADE_IN_TICKS, 0.0f, 1.0f);
        return 0.35f + 0.65f * t;
    }

    // ==== drawing ================================================================================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || STATES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float partial = event.getPartialTick();
        long now = level.getGameTime();
        float time = (now % 72000L) + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        float skyDarken = level.getSkyDarken(partial);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);

        // The jelly: closed surface, so only its outside is drawn.
        RenderSystem.enableCull();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (State state : STATES.values()) {
            Vec3 base = base(state);
            jelly(buffer, m, state, base, gather(state, now, partial), vivid(state, now, partial),
                    light(level, base.add(0, 0.5, 0), skyDarken), time);
        }
        BufferUploader.drawWithShader(buffer.end());

        // The pseudopods: camera-facing strips.
        RenderSystem.disableCull();
        buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (State state : STATES.values()) {
            float vivid = vivid(state, now, partial);
            float light = light(level, base(state).add(0, 1, 0), skyDarken);
            double scale = Math.sqrt(Math.max(1.0, state.entry.size()));
            for (Lash lash : state.lashes) lash(buffer, m, lash, now, partial, time, cam, vivid, light, scale);
        }
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static float light(ClientLevel level, Vec3 p, float skyDarken) {
        int packed = LevelRenderer.getLightColor(level, BlockPos.containing(p));
        float block = LightTexture.block(packed) / 15.0f;
        float sky = LightTexture.sky(packed) / 15.0f * skyDarken;
        return 0.22f + 0.78f * Math.max(block, sky);
    }

    private static void jelly(BufferBuilder buffer, Matrix4f m, State state, Vec3 base, float g, float vivid, float light, float time) {
        double size = state.entry.size();
        double puddle = size * 0.5 * 0.92;
        double dome = Amoeba.domeRadius(size);
        long seed = state.entry.pos().asLong();
        float s0 = (seed & 0xFFFF) / 6553.6f;
        Vec3[][] p = new Vec3[RINGS + 1][SEGMENTS + 1];
        int[][] col = new int[RINGS + 1][SEGMENTS + 1];
        int[][] alpha = new int[RINGS + 1][SEGMENTS + 1];
        for (int i = 0; i <= RINGS; i++) {
            double rho = i / (double) RINGS;
            double theta = rho * Math.PI * 0.5;
            for (int j = 0; j <= SEGMENTS; j++) {
                double phi = Math.PI * 2.0 * j / SEGMENTS;
                double edge = 0.84 + 0.1 * Math.sin(phi * 3.0 + s0) + 0.06 * Math.sin(phi * 5.0 - s0 * 1.3 + time * 0.01);
                double rP = rho * puddle * edge;
                double hP = 0.035 * (1.0 - rho * rho) + 0.008 * Math.sin(rho * 9.0 - time * 0.12 + s0) * (1.0 - rho);
                double wobble = 1.0 + 0.06 * Math.sin(phi * 3.0 + time * 0.4 + s0) + 0.04 * Math.sin(phi * 5.0 - time * 0.3 + rho * 4.0);
                double pulse = 1.0 + 0.05 * Math.sin(time * 0.5 + s0);
                double rD = dome * Math.sin(theta) * wobble * pulse;
                double hD = dome * Math.cos(theta) * 1.05 * pulse;
                double r = Mth.lerp(g, rP, rD);
                double h = Mth.lerp(g, hP, hD) + 0.012;
                p[i][j] = base.add(Math.cos(phi) * r, h, Math.sin(phi) * r);
                // Colour: marsh green with pinkish patches, lighter on top; pales when spent.
                double patch = 0.5 + 0.5 * Math.sin(phi * 2.0 + rho * 5.0 + s0 + time * 0.01);
                int c = mix(GEL, GEL_PINK, (float) (0.35 * patch * patch));
                c = mix(c, GEL_LIGHT, (float) (0.45 * (1.0 - rho) * (0.4 + 0.6 * g)));
                c = mix(GEL_PALE, c, vivid);
                float shade = light * (0.8f + 0.2f * (float) Math.cos(theta * g));
                col[i][j] = shade(c, shade);
                float a = (float) Mth.lerp(g, 0.78 - 0.45 * rho * rho, 0.86 - 0.2 * rho) * (0.7f + 0.3f * vivid);
                alpha[i][j] = (int) (255 * a);
            }
        }
        Vec3 below = base.subtract(0.0, 1.0, 0.0);
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                Vec3 a = p[i][j];
                Vec3 b = p[i][j + 1];
                Vec3 c = p[i + 1][j + 1];
                Vec3 d = p[i + 1][j];
                Vec3 n = b.subtract(a).cross(d.subtract(a));
                if (n.lengthSqr() < 1.0E-12) n = c.subtract(b).cross(a.subtract(b));
                boolean outward = n.dot(a.add(c).scale(0.5).subtract(below)) >= 0.0;
                if (outward) {
                    put(buffer, m, a, col[i][j], alpha[i][j]);
                    put(buffer, m, b, col[i][j + 1], alpha[i][j + 1]);
                    put(buffer, m, c, col[i + 1][j + 1], alpha[i + 1][j + 1]);
                    put(buffer, m, d, col[i + 1][j], alpha[i + 1][j]);
                } else {
                    put(buffer, m, d, col[i + 1][j], alpha[i + 1][j]);
                    put(buffer, m, c, col[i + 1][j + 1], alpha[i + 1][j + 1]);
                    put(buffer, m, b, col[i][j + 1], alpha[i][j + 1]);
                    put(buffer, m, a, col[i][j], alpha[i][j]);
                }
            }
        }
    }

    private static void lash(BufferBuilder buffer, Matrix4f m, Lash lash, long now, float partial, float time, Vec3 cam,
                             float vivid, float light, double scale) {
        float t = now - lash.start() + partial;
        float extend;
        if (t < Amoeba.LASH_EXTEND) {
            float e = t / Amoeba.LASH_EXTEND;
            extend = 1.0f - (1.0f - e) * (1.0f - e);
        } else if (t < Amoeba.LASH_EXTEND + Amoeba.LASH_HOLD) {
            extend = 1.0f;
        } else {
            extend = 1.0f - Mth.clamp((t - Amoeba.LASH_EXTEND - Amoeba.LASH_HOLD) / Amoeba.LASH_RETRACT, 0.0f, 1.0f);
        }
        if (extend <= 0.02f) return;
        int n = 16;
        Vec3[] pts = new Vec3[n + 1];
        Vec3 axis = lash.tip().subtract(lash.origin());
        Vec3 side = axis.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 0; i <= n; i++) {
            double s = extend * i / (double) n;
            double writhe = 0.08 * s * Math.sin(s * 9.0 - time * 0.9 + lash.seed());
            pts[i] = Amoeba.lashPoint(lash.origin(), lash.tip(), s).add(side.scale(writhe))
                    .add(0.0, 0.05 * s * Math.cos(s * 7.0 - time * 0.7 + lash.seed()), 0.0);
        }
        int c = shade(mix(GEL_PALE, mix(GEL, GEL_LIGHT, 0.3f), vivid), light);
        for (int i = 0; i < n; i++) {
            double s0 = i / (double) n;
            double s1 = (i + 1) / (double) n;
            double w0 = (0.13 - 0.1 * s0) * scale;
            double w1 = (0.13 - 0.1 * s1) * scale;
            Vec3 a = pts[i];
            Vec3 b = pts[i + 1];
            Vec3 tangent = b.subtract(a);
            Vec3 toCam = cam.subtract(a);
            Vec3 across = tangent.cross(toCam);
            if (across.lengthSqr() < 1.0E-10) continue;
            across = across.normalize();
            int a0 = (int) (220 * (1.0 - 0.3 * s0));
            int a1 = (int) (220 * (1.0 - 0.3 * s1));
            put(buffer, m, a.subtract(across.scale(w0)), c, a0);
            put(buffer, m, b.subtract(across.scale(w1)), c, a1);
            put(buffer, m, b.add(across.scale(w1)), c, a1);
            put(buffer, m, a.add(across.scale(w0)), c, a0);
        }
    }

    private static void put(BufferBuilder buffer, Matrix4f m, Vec3 p, int rgb, int alpha) {
        buffer.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Mth.clamp(alpha, 0, 255)).endVertex();
    }

    private static int shade(int rgb, float light) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * light));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * light));
        int b = Math.min(255, (int) ((rgb & 0xFF) * light));
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
