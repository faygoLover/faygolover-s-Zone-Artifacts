package faygolover.zoneartifacts.client.gravity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Gore for Voronka and Karusel kills: the body vanishes at once (instead of the vanilla death
 * tilt), drops of blood fly off, and blotches of blood are left on the blocks around, fading after
 * a minute or so.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GoreClient {

    private static final int MAX_DECALS = 300;
    private static final int FADE_TICKS = 200;

    /** Entities whose body is hidden, until (game time). */
    private static final Map<Integer, Long> HIDDEN = new HashMap<>();
    private static final List<Decal> DECALS = new ArrayList<>();
    /** Decals belong to one world: cleared when the level changes (dimension change, reconnect). */
    private static ClientLevel lastLevel;

    /** A blotch on a block face: an irregular blob, seeded, darker in the middle (blood, or a
     *  chemical burn — {@link #addStain}). */
    private record Decal(Vec3 center, Vec3 normal, Vec3 t, Vec3 b, double size, long seed, long born, long dies,
                         int outerRgb, int outerAlpha, int innerRgb, int innerAlpha) {
    }

    private static final int BLOOD_OUTER = 0x5F0404;
    private static final int BLOOD_INNER = 0x3C0202;

    /**
     * A temporary stain where a ray from {@code from} along {@code dir} meets a block, within
     * {@code reach}: chemical burns (Chemical Comet, Burning Fluff, Amoeba) and the like. Fades
     * after {@code lifeTicks}. {@code darkRgb}: the middle, {@code rgb}: the rim.
     */
    public static void addStain(ClientLevel level, Vec3 from, Vec3 dir, double reach, double size,
                                int rgb, int darkRgb, int alpha, int lifeTicks) {
        if (level != lastLevel) {
            DECALS.clear();
            HIDDEN.clear();
            lastLevel = level;
        }
        RandomSource random = RandomSource.create();
        addDecal(level, from, dir, reach, size, random, level.getGameTime(), lifeTicks, rgb, alpha, darkRgb, Math.min(255, alpha + 30));
    }

    private GoreClient() {
    }

    public static void onGore(int entityId, double x, double y, double z, float width, float height) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = level.getGameTime();
        HIDDEN.put(entityId, now + 60);

        RandomSource random = RandomSource.create();
        double volume = Math.max(0.3, width * width * height);
        Vec3 center = new Vec3(x, y + height * 0.5, z);

        // Drops flying off.
        int drops = (int) Mth.clamp(40 + 60 * volume, 40, 220);
        for (int i = 0; i < drops; i++) {
            Vec3 p = new Vec3(x + (random.nextDouble() - 0.5) * width, y + random.nextDouble() * height, z + (random.nextDouble() - 0.5) * width);
            Vec3 v = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.8 + 0.4, random.nextGaussian())
                    .normalize().scale(0.12 + random.nextDouble() * 0.3);
            level.addParticle(ModParticles.BLOOD.get(), p.x, p.y, p.z, v.x, v.y, v.z);
        }

        // Blotches where the spray lands: a big one under the body, more around.
        addDecal(level, center, new Vec3(0, -1, 0), height + 2.0, 0.5 + 0.3 * width, random, now);
        int rays = 10 + (int) (6 * volume);
        for (int i = 0; i < rays; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), -Math.abs(random.nextGaussian()) - 0.2 + random.nextDouble() * 0.6, random.nextGaussian()).normalize();
            addDecal(level, center, dir, 3.0, 0.2 + random.nextDouble() * 0.45, random, now);
        }
    }

    private static void addDecal(ClientLevel level, Vec3 from, Vec3 dir, double reach, double size, RandomSource random, long now) {
        addDecal(level, from, dir, reach, size, random, now, 800 + random.nextInt(500), BLOOD_OUTER, 170, BLOOD_INNER, 200);
    }

    private static void addDecal(ClientLevel level, Vec3 from, Vec3 dir, double reach, double size, RandomSource random, long now,
                                 int lifeTicks, int outerRgb, int outerAlpha, int innerRgb, int innerAlpha) {
        BlockHitResult hit = Razlom.clipBlocks(level, from, from.add(dir.scale(reach)));
        if (hit.getType() == HitResult.Type.MISS) return;
        Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 helper = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        double angle = random.nextDouble() * Math.PI * 2.0;
        Vec3 t0 = n.cross(helper).normalize();
        Vec3 b0 = n.cross(t0).normalize();
        Vec3 t = t0.scale(Math.cos(angle)).add(b0.scale(Math.sin(angle)));
        Vec3 b = n.cross(t).normalize();
        DECALS.add(new Decal(hit.getLocation().add(n.scale(0.004 + random.nextDouble() * 0.004)), n, t, b, size, random.nextLong(), now, now + lifeTicks,
                outerRgb, outerAlpha, innerRgb, innerAlpha));
        while (DECALS.size() > MAX_DECALS) DECALS.remove(0);
    }

    /** The torn body is not drawn during its death animation. */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (HIDDEN.isEmpty()) return;
        Long until = HIDDEN.get(event.getEntity().getId());
        if (until != null && event.getEntity().isDeadOrDying()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            DECALS.clear();
            HIDDEN.clear();
            return;
        }
        if (mc.level != lastLevel) {
            DECALS.clear();
            HIDDEN.clear();
            lastLevel = mc.level;
        }
        long now = mc.level.getGameTime();
        HIDDEN.values().removeIf(until -> until < now);
        if (DECALS.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // Vanilla leaves the depth test off after the translucent layer: without this the
        // shapes would show through blocks.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Iterator<Decal> it = DECALS.iterator(); it.hasNext(); ) {
            Decal decal = it.next();
            if (now >= decal.dies()) {
                it.remove();
                continue;
            }
            if (decal.center().distanceToSqr(cam) > 64 * 64) continue;
            float fade = Math.min(1.0f, (decal.dies() - now) / (float) FADE_TICKS);
            RandomSource shape = RandomSource.create(decal.seed());
            int o = decal.outerRgb();
            int in = decal.innerRgb();
            blob(buffer, matrix, decal, 1.0, shape, (int) (decal.outerAlpha() * fade), (o >> 16) & 0xFF, (o >> 8) & 0xFF, o & 0xFF);
            blob(buffer, matrix, decal, 0.55, shape, (int) (decal.innerAlpha() * fade), (in >> 16) & 0xFF, (in >> 8) & 0xFF, in & 0xFF);
        }

        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** An irregular round blob on the decal's face, as a fan of triangles (quads with a repeated corner). */
    private static void blob(BufferBuilder buffer, Matrix4f m, Decal d, double scale, RandomSource shape,
                             int alpha, int r, int g, int b) {
        int points = 12;
        double[] radii = new double[points];
        for (int i = 0; i < points; i++) radii[i] = d.size() * scale * (0.6 + shape.nextDouble() * 0.45);
        Vec3 c = d.center();
        for (int i = 0; i < points; i++) {
            double a0 = Math.PI * 2.0 * i / points;
            double a1 = Math.PI * 2.0 * (i + 1) / points;
            Vec3 p0 = c.add(d.t().scale(Math.cos(a0) * radii[i])).add(d.b().scale(Math.sin(a0) * radii[i]));
            Vec3 p1 = c.add(d.t().scale(Math.cos(a1) * radii[(i + 1) % points])).add(d.b().scale(Math.sin(a1) * radii[(i + 1) % points]));
            buffer.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(r, g, b, alpha).endVertex();
            buffer.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, (int) (alpha * 0.85)).endVertex();
            buffer.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, (int) (alpha * 0.85)).endVertex();
            buffer.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, (int) (alpha * 0.85)).endVertex();
        }
    }
}
