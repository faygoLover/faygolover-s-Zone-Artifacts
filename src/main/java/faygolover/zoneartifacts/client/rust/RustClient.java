package faygolover.zoneartifacts.client.rust;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.RustEngine;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.client.ZoneLoopSound;
import faygolover.zoneartifacts.client.chem.Gas;
import faygolover.zoneartifacts.client.distortion.Distortion;
import faygolover.zoneartifacts.client.gravity.GoreClient;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.RustPacket;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rust's look: every open face of the blocks in the zone is overgrown with rusty moss — a mat with
 * bald patches and two thin layers of fuzz over it (tiled textures, lit by the world) — rusty dust
 * kicked up by anyone walking in it, and the red-hot patch: the moss there darkens to a deep red,
 * glows and smoulders, embers rise and the air shimmers over it; its blast and its cooling.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RustClient {

    private static final ResourceLocation MOSS = new ResourceLocation(ZoneArtifacts.MODID, "textures/misc/rust_moss.png");
    private static final ResourceLocation FUZZ_A = new ResourceLocation(ZoneArtifacts.MODID, "textures/misc/rust_fuzz_a.png");
    private static final ResourceLocation FUZZ_B = new ResourceLocation(ZoneArtifacts.MODID, "textures/misc/rust_fuzz_b.png");
    private static final double NEAR = 64.0;
    private static final int MAX_FACES = 6000;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Direction[] DIRS = Direction.values();

    private static final class Zone {
        SyncAnomaliesPacket.Entry entry;
        int[] faces = new int[0]; // x, y, z, direction per face
        int nextScan;
        @Nullable
        Vec3 spot;
        long spotBorn;
        long spotSeen;
        @Nullable
        ZoneLoopSound crackle;
        /** The moss layers, built once per scan (not every frame) around {@link #origin}. */
        final VertexBuffer[] layers = new VertexBuffer[LAYERS.length];
        boolean dirty = true;
        BlockPos origin = BlockPos.ZERO;

        void close() {
            for (int i = 0; i < layers.length; i++) {
                if (layers[i] != null) layers[i].close();
                layers[i] = null;
            }
        }
    }

    private static final ResourceLocation[] LAYERS = {MOSS, FUZZ_A, FUZZ_B};
    private static final RenderType[] LAYER_TYPES = {MossRenderType.of(MOSS), MossRenderType.of(FUZZ_A), MossRenderType.of(FUZZ_B)};
    private static final double[] LAYER_OFFSETS = {0.003, 0.028, 0.055};

    private static final Map<BlockPos, Zone> ZONES = new HashMap<>();
    private static final Map<Entity, Long> LAST_DUST = new java.util.WeakHashMap<>();

    private RustClient() {
    }

    public static void onPacket(BlockPos zonePos, int type, Vec3 at, int age) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = level.getGameTime();
        Zone z = ZONES.computeIfAbsent(zonePos, p -> new Zone());
        switch (type) {
            case RustPacket.SPOT -> {
                if (z.spot == null || z.spot.distanceToSqr(at) > 0.01) z.spotBorn = now - age;
                z.spot = at;
                z.spotSeen = now;
            }
            case RustPacket.BLAST -> {
                z.spot = null;
                blast(level, at, now);
            }
            case RustPacket.DISCHARGE -> {
                z.spot = null;
                for (int i = 0; i < 14; i++) {
                    Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.03, 0.05 + RANDOM.nextDouble() * 0.05, RANDOM.nextGaussian() * 0.03);
                    Gas.add(new Gas.Puff(at.add(RANDOM.nextGaussian() * 0.6, 0.1, RANDOM.nextGaussian() * 0.6), v, 0.3, 1.1, now,
                            30 + RANDOM.nextInt(20), 0.35f, 0xD8DCDE, 0xF2F4F5, RANDOM.nextFloat() * 10f).drag(0.94));
                }
            }
            default -> z.spot = null;
        }
    }

    /** How hot the patch is: 0..1 as it heats up (then it stays hot). */
    private static float heat(Zone z, long now, float partial) {
        if (z.spot == null) return 0.0f;
        float t = Mth.clamp((now - z.spotBorn + partial) / (float) RustEngine.ARM_TICKS, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static void blast(ClientLevel level, Vec3 at, long now) {
        double r = ModCommonConfig.RUST_SPOT_RADIUS.get();
        for (int i = 0; i < 26; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), Math.abs(RANDOM.nextGaussian()) + 0.4, RANDOM.nextGaussian()).normalize();
            Gas.add(new Gas.Puff(at.add(0.0, 0.2, 0.0), dir.scale(0.08 + RANDOM.nextDouble() * 0.12), 0.3, 1.0 + RANDOM.nextDouble() * 0.6,
                    now, 20 + RANDOM.nextInt(20), 0.55f, 0xB0381A, 0xE0702A, RANDOM.nextFloat() * 10f).drag(0.88).glow(0.9f));
        }
        for (int i = 0; i < 40; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.15, 0.15 + RANDOM.nextDouble() * 0.3, RANDOM.nextGaussian() * 0.15);
            faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, ModParticles.EMBER.get(), at.x, at.y + 0.2, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 16; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), -Math.abs(RANDOM.nextGaussian()) - 0.2, RANDOM.nextGaussian()).normalize();
            GoreClient.addStain(level, at.add(0.0, 0.6, 0.0), dir, r * 1.5, 0.25 + RANDOM.nextDouble() * 0.4,
                    0x5A1A0C, 0x2A0A04, 150, 900 + RANDOM.nextInt(300));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            ZONES.values().forEach(Zone::close);
            ZONES.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Set<BlockPos> seen = new HashSet<>();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.RUST.equals(entry.typeId())) continue;
            if (Vec3.atCenterOf(entry.pos()).distanceTo(cam) > NEAR + entry.size()) continue;
            seen.add(entry.pos());
            Zone z = ZONES.computeIfAbsent(entry.pos(), p -> new Zone());
            boolean resized = z.entry == null || z.entry.size() != entry.size();
            z.entry = entry;
            if (resized || --z.nextScan <= 0) {
                scan(level, z);
                z.nextScan = 100 + RANDOM.nextInt(20);
            }
            if (z.spot != null && now - z.spotSeen > 400) z.spot = null; // lost track of it
            dust(level, z, now);
            smoulder(mc, level, z, now);
        }
        ZONES.entrySet().removeIf(e -> {
            if (seen.contains(e.getKey())) return false;
            e.getValue().close();
            return true;
        });
    }

    /** Every open face of the solid blocks in the zone. */
    private static void scan(ClientLevel level, Zone z) {
        AABB zone = AnomalyGeometry.box(z.entry);
        int[] out = new int[MAX_FACES * 4];
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(zone.minX); x <= Mth.floor(zone.maxX - 1.0E-6) && n < MAX_FACES; x++) {
            for (int y = Mth.floor(zone.minY); y <= Mth.floor(zone.maxY - 1.0E-6) && n < MAX_FACES; y++) {
                for (int zz = Mth.floor(zone.minZ); zz <= Mth.floor(zone.maxZ - 1.0E-6) && n < MAX_FACES; zz++) {
                    p.set(x, y, zz);
                    BlockState state = level.getBlockState(p);
                    if (!state.isCollisionShapeFullBlock(level, p)) continue;
                    for (Direction d : DIRS) {
                        q.setWithOffset(p, d);
                        if (level.getBlockState(q).isCollisionShapeFullBlock(level, q)) continue;
                        out[n * 4] = x;
                        out[n * 4 + 1] = y;
                        out[n * 4 + 2] = zz;
                        out[n * 4 + 3] = d.ordinal();
                        if (++n >= MAX_FACES) break;
                    }
                }
            }
        }
        z.faces = java.util.Arrays.copyOf(out, n * 4);
        z.dirty = true;
    }

    /** Bakes the moss of a zone into its vertex buffers: positions around its origin, light and shade in the colour. */
    private static void build(ClientLevel level, Zone z) {
        z.dirty = false;
        z.origin = z.entry.pos();
        int[] f = z.faces;
        int[] light = new int[f.length / 4];
        BlockPos.MutableBlockPos lp = new BlockPos.MutableBlockPos();
        for (int i = 0, k = 0; i + 3 < f.length; i += 4, k++) {
            Direction d = DIRS[f[i + 3]];
            light[k] = LevelRenderer.getLightColor(level, lp.set(f[i] + d.getStepX(), f[i + 1] + d.getStepY(), f[i + 2] + d.getStepZ()));
        }
        for (int layer = 0; layer < LAYERS.length; layer++) {
            if (f.length == 0) {
                if (z.layers[layer] != null) z.layers[layer].close();
                z.layers[layer] = null;
                continue;
            }
            BufferBuilder builder = new BufferBuilder(f.length * DefaultVertexFormat.BLOCK.getVertexSize() + 256);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (int i = 0, k = 0; i + 3 < f.length; i += 4, k++) {
                Direction d = DIRS[f[i + 3]];
                int shade = (int) (255 * level.getShade(d, true));
                face(builder, null, z.origin, f[i], f[i + 1], f[i + 2], d, LAYER_OFFSETS[layer], shade, shade, shade, light[k], layer);
            }
            if (z.layers[layer] == null) z.layers[layer] = new VertexBuffer(VertexBuffer.Usage.STATIC);
            z.layers[layer].bind();
            z.layers[layer].upload(builder.end());
            VertexBuffer.unbind();
        }
    }

    /** Rusty dust at the feet of whoever walks in it (not sneaking). */
    private static void dust(ClientLevel level, Zone z, long now) {
        AABB zone = AnomalyGeometry.box(z.entry);
        int eff = ModClientConfig.effective(z.entry.intensity());
        List<LivingEntity> walkers = level.getEntitiesOfClass(LivingEntity.class, zone, e -> e.isAlive() && !e.isSpectator());
        for (LivingEntity e : walkers) {
            double moved = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
            if (!e.onGround() || e.isCrouching() || moved < 0.04) continue;
            Long last = LAST_DUST.get(e);
            if (last != null && now - last < 4) continue;
            LAST_DUST.put(e, now);
            int count = 1 + eff / 2;
            for (int i = 0; i < count; i++) {
                Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.02, 0.015 + RANDOM.nextDouble() * 0.02, RANDOM.nextGaussian() * 0.02);
                Gas.add(new Gas.Puff(e.position().add(RANDOM.nextGaussian() * 0.2, 0.1, RANDOM.nextGaussian() * 0.2), v, 0.15, 0.6,
                        now, 35 + RANDOM.nextInt(20), 0.4f, 0x7A3E1C, 0xA8622E, RANDOM.nextFloat() * 10f)
                        .settle(e.getY() + 0.4).drag(0.93));
            }
        }
    }

    /** The red-hot patch: embers, a crackle. */
    private static void smoulder(Minecraft mc, ClientLevel level, Zone z, long now) {
        float heat = heat(z, now, 0.0f);
        if (z.spot == null) return;
        double r = ModCommonConfig.RUST_SPOT_RADIUS.get();
        if (RANDOM.nextFloat() < 0.3f + 0.5f * heat) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0;
            double d = r * Math.sqrt(RANDOM.nextDouble());
            faygolover.zoneartifacts.client.ClientAnomalyCache.particle(level, ModParticles.EMBER.get(), z.spot.x + Math.cos(a) * d, z.spot.y + 0.05, z.spot.z + Math.sin(a) * d,
                    0.0, 0.02 + RANDOM.nextDouble() * 0.03 * heat, 0.0);
        }
        if (z.crackle == null || z.crackle.isStopped()) {
            Zone zz = z;
            BlockPos pos = z.entry.pos();
            z.crackle = new ZoneLoopSound(ModSounds.RUST_CRACKLE.get(), z.spot.add(0.0, 0.2, 0.0),
                    () -> ZONES.get(pos) == zz && zz.spot != null, () -> 0.2 + 0.4 * heat(zz, Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime(), 0.0f));
            mc.getSoundManager().play(z.crackle);
        }
    }

    /** The air shimmering over the patch. */
    public static void collect(List<Distortion.Patch> out, long now, float partial) {
        float time = (now % 72000L) + partial;
        for (Zone z : ZONES.values()) {
            if (z.spot == null) continue;
            float heat = heat(z, now, partial);
            double r = ModCommonConfig.RUST_SPOT_RADIUS.get();
            out.add(new Distortion.Haze(z.spot, r * 2.0, 1.4, 0.03 * heat, time * 0.05, 2.0, false, 1.0f, z.spot.x * 0.1));
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || ZONES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = level.getGameTime();
        float partial = event.getPartialTick();
        float time = (now % 72000L) + partial;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        // Before the camera offset: the moss buffers are placed relative to it in float precision.
        Matrix4f view = new Matrix4f(poseStack.last().pose());
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double r = ModCommonConfig.RUST_SPOT_RADIUS.get();

        // The moss: ready-made layers, one draw each.
        for (int layer = 0; layer < LAYERS.length; layer++) {
            RenderType type = LAYER_TYPES[layer];
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0.0f, 0.0f, 0.0f);
                for (Zone z : ZONES.values()) {
                    if (z.entry != null && !z.entry.visible()) continue;
                    if (z.entry == null) continue;
                    if (z.dirty) build(level, z);
                    VertexBuffer vbo = z.layers[layer];
                    if (vbo == null) continue;
                    Matrix4f mv = new Matrix4f(view).translate((float) (z.origin.getX() - cam.x), (float) (z.origin.getY() - cam.y),
                            (float) (z.origin.getZ() - cam.z));
                    vbo.bind();
                    vbo.drawWithShader(mv, RenderSystem.getProjectionMatrix(), shader);
                }
                VertexBuffer.unbind();
            }
            type.clearRenderState();
        }

        // Where it's red-hot: those few faces again over the top, tinted, every frame.
        BlockPos.MutableBlockPos lp = new BlockPos.MutableBlockPos();
        for (int layer = 0; layer < LAYERS.length; layer++) {
            boolean any = false;
            RenderType type = LAYER_TYPES[layer];
            for (Zone z : ZONES.values()) {
                if (z.entry != null && !z.entry.visible()) continue;
                if (z.entry == null || z.spot == null) continue;
                float heat = heat(z, now, partial);
                if (heat <= 0.01f) continue;
                VertexConsumer vc = buffers.getBuffer(type);
                any = true;
                int[] f = z.faces;
                for (int i = 0; i + 3 < f.length; i += 4) {
                    Direction d = DIRS[f[i + 3]];
                    double dist = Math.sqrt(sq(f[i] + 0.5 + d.getStepX() * 0.5 - z.spot.x) + sq(f[i + 1] + 0.5 + d.getStepY() * 0.5 - z.spot.y)
                            + sq(f[i + 2] + 0.5 + d.getStepZ() * 0.5 - z.spot.z));
                    float h = heat * (float) Mth.clamp(1.0 - (dist - r * 0.5) / (r * 0.8), 0.0, 1.0);
                    if (h <= 0.01f) continue;
                    int light = LevelRenderer.getLightColor(level, lp.set(f[i] + d.getStepX(), f[i + 1] + d.getStepY(), f[i + 2] + d.getStepZ()));
                    float shade = level.getShade(d, true);
                    face(vc, m, null, f[i], f[i + 1], f[i + 2], d, LAYER_OFFSETS[layer] + 0.002, (int) (255 * shade),
                            (int) (Mth.lerp(h, 255, 95) * shade), (int) (Mth.lerp(h, 255, 80) * shade), light, layer);
                }
            }
            if (any) buffers.endBatch(type);
        }

        // The glow of the red-hot moss (drawn after, additively).
        VertexConsumer glow = buffers.getBuffer(GlowRenderType.GLOW);
        for (Zone z : ZONES.values()) {
            if (z.entry != null && !z.entry.visible()) continue;
            if (z.spot == null || z.entry == null) continue;
            float heat = heat(z, now, partial);
            float pulse = 0.8f + 0.2f * Mth.sin(time * 0.09f) + 0.08f * Mth.sin(time * 0.31f);
            int[] f = z.faces;
            for (int i = 0; i + 3 < f.length; i += 4) {
                Direction d = DIRS[f[i + 3]];
                double dist = Math.sqrt(sq(f[i] + 0.5 + d.getStepX() * 0.5 - z.spot.x) + sq(f[i + 1] + 0.5 + d.getStepY() * 0.5 - z.spot.y)
                        + sq(f[i + 2] + 0.5 + d.getStepZ() * 0.5 - z.spot.z));
                float h = heat * (float) Mth.clamp(1.0 - (dist - r * 0.5) / (r * 0.8), 0.0, 1.0);
                if (h <= 0.01f) continue;
                glowFace(glow, m, f[i], f[i + 1], f[i + 2], d, 0.06, (int) (255 * 0.4f * h * pulse));
            }
        }
        buffers.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    private static double sq(double v) {
        return v * v;
    }

    /** One face of a block, lifted {@code off} off it, the texture tiled over two blocks. Through {@code m}
     *  (drawn now), or else relative to {@code origin} (into a vertex buffer). */
    private static void face(VertexConsumer vc, @Nullable Matrix4f m, @Nullable BlockPos origin, int x, int y, int z, Direction d,
                             double off, int r, int g, int b, int light, int layer) {
        float[][] c = corners(x, y, z, d, off);
        Vector3f n = d.step();
        // World-space tiling, two blocks per texture: which quarter of the tile this face is.
        int aMin;
        int bMin;
        switch (d.getAxis()) {
            case Y -> {
                aMin = x;
                bMin = z;
            }
            case Z -> {
                aMin = x;
                bMin = -(y + 1);
            }
            default -> {
                aMin = z;
                bMin = -(y + 1);
            }
        }
        // The layers are offset by a block, so the fuzz doesn't sit exactly over the mat's pattern.
        int baseA = aMin + layer - Math.floorMod(aMin + layer, 2);
        int baseB = bMin + layer * 3 - Math.floorMod(bMin + layer * 3, 2);
        for (float[] p : c) {
            float a;
            float bb;
            switch (d.getAxis()) {
                case Y -> {
                    a = p[0];
                    bb = p[2];
                }
                case Z -> {
                    a = p[0];
                    bb = -p[1];
                }
                default -> {
                    a = p[2];
                    bb = -p[1];
                }
            }
            float u = Mth.clamp((a + layer - baseA) * 0.5f, 0.0f, 1.0f);
            float v = Mth.clamp((bb + layer * 3 - baseB) * 0.5f, 0.0f, 1.0f);
            if (m != null) {
                vc.vertex(m, p[0], p[1], p[2]);
            } else {
                vc.vertex(p[0] - origin.getX(), p[1] - origin.getY(), p[2] - origin.getZ());
            }
            vc.color(r, g, b, 255).uv(u, v).uv2(light).normal(n.x(), n.y(), n.z()).endVertex();
        }
    }

    private static void glowFace(VertexConsumer vc, Matrix4f m, int x, int y, int z, Direction d, double off, int alpha) {
        float[][] c = corners(x, y, z, d, off);
        for (float[] p : c) {
            vc.vertex(m, p[0], p[1], p[2]).color(255, 70, 20, Mth.clamp(alpha, 0, 255)).endVertex();
        }
    }

    /** The four corners of a face (in a consistent winding), pushed {@code off} out along its normal. */
    private static float[][] corners(int x, int y, int z, Direction d, double off) {
        float ox = (float) (d.getStepX() * off);
        float oy = (float) (d.getStepY() * off);
        float oz = (float) (d.getStepZ() * off);
        float x0 = x, y0 = y, z0 = z, x1 = x + 1, y1 = y + 1, z1 = z + 1;
        return switch (d) {
            case UP -> new float[][]{{x0, y1 + oy, z0}, {x0, y1 + oy, z1}, {x1, y1 + oy, z1}, {x1, y1 + oy, z0}};
            case DOWN -> new float[][]{{x0, y0 + oy, z0}, {x1, y0 + oy, z0}, {x1, y0 + oy, z1}, {x0, y0 + oy, z1}};
            case NORTH -> new float[][]{{x0, y0, z0 + oz}, {x0, y1, z0 + oz}, {x1, y1, z0 + oz}, {x1, y0, z0 + oz}};
            case SOUTH -> new float[][]{{x0, y0, z1 + oz}, {x1, y0, z1 + oz}, {x1, y1, z1 + oz}, {x0, y1, z1 + oz}};
            case WEST -> new float[][]{{x0 + ox, y0, z0}, {x0 + ox, y0, z1}, {x0 + ox, y1, z1}, {x0 + ox, y1, z0}};
            default -> new float[][]{{x1 + ox, y0, z0}, {x1 + ox, y1, z0}, {x1 + ox, y1, z1}, {x1 + ox, y0, z1}};
        };
    }
}
