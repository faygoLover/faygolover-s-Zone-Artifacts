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
 * slowly rippling puddle at rest; when it wakes it draws together into a quivering dome, rounds into
 * a ball, lifts off and floats up swelling, wobbling harder and harder, dripping acid — and bursts
 * (the cloud is drawn like the Chemical Comet's). A pale puddle seeps back and slowly regains its
 * colour over the cooldown.
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

    private static final class State {
        SyncAnomaliesPacket.Entry entry;
        long gatherTick = -1;
        long popTick = -1_000_000L;
        boolean resting;
        long readySince = -1_000_000L;
        long restSince = -1_000_000L;
        Double groundY;
        int nextScan;
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();

    private AmoebaClient() {
    }

    private static int inflateTicks() {
        try {
            return Math.max(Amoeba.GATHER_TICKS + Amoeba.LIFT_TICKS + 10, (int) Math.round(ModCommonConfig.AMOEBA_INFLATE_SECONDS.get() * 20.0));
        } catch (IllegalStateException notLoaded) {
            return 120;
        }
    }

    public static void onEvent(BlockPos pos, byte event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State state = STATES.get(pos);
        if (state == null) return;
        long now = mc.level.getGameTime();
        if (event == AmoebaEventPacket.GATHER) {
            state.gatherTick = now;
        } else if (event == AmoebaEventPacket.POP) {
            state.gatherTick = -1;
            state.popTick = now;
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
            if (entry.active() && state.gatherTick < 0) state.gatherTick = now - Amoeba.GATHER_TICKS; // came in mid-way
            if (!entry.active() && state.gatherTick >= 0 && now - state.gatherTick > inflateTicks() + 10) {
                state.gatherTick = -1;
            }
            if (--state.nextScan <= 0) {
                AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
                Vec3 c = zone.getCenter();
                state.groundY = Razlom.groundY(level, c.x, c.z, zone.maxY, zone.minY - 3.0);
                state.nextScan = 60 + RANDOM.nextInt(20);
            }
            if (state.gatherTick >= 0 && RANDOM.nextInt(8) == 0) {
                // Acid dripping off the ball.
                float t = now - state.gatherTick;
                Vec3 base = base(state);
                double r = Amoeba.radius(entry.size(), t, inflateTicks());
                double h = Amoeba.centerHeight(entry.size(), t, inflateTicks());
                double a = RANDOM.nextDouble() * Math.PI * 2.0;
                level.addParticle(ModParticles.CHEM_DROP.get(), base.x + Math.cos(a) * r * 0.5, base.y + h - r * 0.8,
                        base.z + Math.sin(a) * r * 0.5, 0.0, -0.02, 0.0);
            }
        }
        STATES.keySet().removeIf(pos -> !seen.contains(pos));
    }

    private static Vec3 base(State state) {
        AABB zone = AnomalyGeometry.centeredAabb(state.entry.pos(), state.entry.size());
        Vec3 c = zone.getCenter();
        return new Vec3(c.x, state.groundY != null ? state.groundY : zone.minY, c.z);
    }

    /** Ticks into the rise (with the partial tick), or -1 at rest. */
    private static float phase(State state, long now, float partial) {
        return state.gatherTick < 0 ? -1.0f : now - state.gatherTick + partial;
    }

    /** The puddle grows back after the burst. */
    private static float regrow(State state, long now, float partial) {
        return Mth.clamp((now - state.popTick + partial) / 60.0f, 0.0f, 1.0f);
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
            jelly(buffer, m, state, base, phase(state, now, partial), regrow(state, now, partial), vivid(state, now, partial),
                    light(level, base.add(0, 0.5, 0), skyDarken), time);
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

    private static void jelly(BufferBuilder buffer, Matrix4f m, State state, Vec3 base, float t, float regrow,
                              float vivid, float light, float time) {
        double size = state.entry.size();
        int inflate = inflateTicks();
        boolean rising = t >= 0.0f;
        float g1 = rising ? Amoeba.gather(t) : 0.0f;
        float g2 = rising ? Amoeba.round(t) : 0.0f;
        float g3 = rising ? Amoeba.swell(t, inflate) : 0.0f;
        double puddle = size * 0.5 * 0.92 * (rising ? 1.0 : 0.25 + 0.75 * regrow);
        double radius = rising ? Amoeba.radius(size, t, inflate) : Amoeba.domeRadius(size);
        double centerY = rising ? Amoeba.centerHeight(size, t, inflate) : 0.0;
        double thetaMax = Math.PI * (0.5 + 0.5 * g2);
        long seed = state.entry.pos().asLong();
        float s0 = (seed & 0xFFFF) / 6553.6f;
        // It sways slowly, like a heavy jelly, and harder as it swells; just before bursting a fine
        // shiver runs over it. (Fixed frequencies: a frequency that changes with the swell jerked
        // the phase, since the time it multiplies is large.)
        double quiver = 0.05 + 0.07 * g3;
        double speed = 0.13;
        double shiver = 0.025 * g3 * g3 * g3;
        Vec3[][] p = new Vec3[RINGS + 1][SEGMENTS + 1];
        int[][] col = new int[RINGS + 1][SEGMENTS + 1];
        int[][] alpha = new int[RINGS + 1][SEGMENTS + 1];
        for (int i = 0; i <= RINGS; i++) {
            double rho = i / (double) RINGS;
            double theta = rho * thetaMax;
            for (int j = 0; j <= SEGMENTS; j++) {
                double phi = Math.PI * 2.0 * j / SEGMENTS;
                double edge = 0.84 + 0.1 * Math.sin(phi * 3.0 + s0) + 0.06 * Math.sin(phi * 5.0 - s0 * 1.3 + time * 0.01);
                double rP = rho * puddle * edge;
                double hP = 0.035 * (1.0 - rho * rho) + 0.008 * Math.sin(rho * 9.0 - time * 0.12 + s0) * (1.0 - rho);
                double wobble = 1.0 + quiver * Math.sin(phi * 3.0 + time * speed + s0) + quiver * 0.7 * Math.sin(phi * 5.0 - time * speed * 0.8 + theta * 4.0)
                        + shiver * Math.sin(phi * 7.0 + theta * 6.0 + time * 0.45 + s0);
                double pulse = 1.0 + (0.03 + 0.04 * g3) * Math.sin(time * 0.1 + s0);
                double rD = radius * Math.sin(theta) * wobble * pulse;
                double hD = centerY + radius * Math.cos(theta) * pulse;
                double r = Mth.lerp(g1, rP, rD);
                double h = Mth.lerp(g1, hP, hD) + 0.012;
                p[i][j] = base.add(Math.cos(phi) * r, h, Math.sin(phi) * r);
                double patch = 0.5 + 0.5 * Math.sin(phi * 2.0 + rho * 5.0 + s0 + time * 0.01);
                int c = mix(GEL, GEL_PINK, (float) (0.35 * patch * patch));
                c = mix(c, GEL_LIGHT, (float) (0.45 * (1.0 - rho) * (0.4 + 0.6 * g1)));
                c = mix(GEL_PALE, c, vivid);
                float shade = light * (0.78f + 0.22f * (float) Math.cos(Math.min(theta, Math.PI * 0.5) * g1));
                col[i][j] = shade(c, shade);
                float a = (float) Mth.lerp(g1, 0.78 - 0.45 * rho * rho, 0.84 - 0.1 * rho) * (0.7f + 0.3f * vivid);
                if (!rising) a *= 0.4f + 0.6f * regrow;
                alpha[i][j] = (int) (255 * a);
            }
        }
        // Inside point, for which way each face looks: below a flat puddle, the ball's middle otherwise.
        Vec3 inside = base.add(0.0, g1 < 1.0f ? -(1.0 - g1) + centerY * g1 : centerY, 0.0);
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                Vec3 a = p[i][j];
                Vec3 b = p[i][j + 1];
                Vec3 c = p[i + 1][j + 1];
                Vec3 d = p[i + 1][j];
                Vec3 n = b.subtract(a).cross(d.subtract(a));
                if (n.lengthSqr() < 1.0E-12) n = c.subtract(b).cross(a.subtract(b));
                boolean outward = n.dot(a.add(c).scale(0.5).subtract(inside)) >= 0.0;
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
