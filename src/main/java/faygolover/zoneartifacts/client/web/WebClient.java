package faygolover.zoneartifacts.client.web;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.GlowRenderType;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.item.PdaItem;
import faygolover.zoneartifacts.item.WebPlacerItem;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.network.SyncWebsPacket;
import faygolover.zoneartifacts.network.WebEditPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Web on the client: threads almost invisible in the dark that catch the light — a faint sheen
 * where there's light enough, sparks of glint running along them, and a bright gleam where a light
 * held in someone's hand points at them. Snapped threads are gone until they grow back. With the Web
 * tool: the threads show plainly, a line follows from the last point to the aim, and a left-click
 * removes a thread (sneaking — the whole web).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WebClient {

    private static final double NEAR = 64.0;
    /** Farther than this from the eye a web doesn't glint at all (it isn't drawn), blocks; it fades out over the last two. */
    private static final double SEEN = 8.0;

    private static final class Strand {
        final Vec3 a;
        final Vec3 b;
        boolean broken;

        Strand(Vec3 a, Vec3 b, boolean broken) {
            this.a = a;
            this.b = b;
            this.broken = broken;
        }
    }

    private record Web(int id, int intensity, List<Strand> strands) {
    }

    /** A thread under the aim: its web, its index, how far along the view. */
    public record Hit(int webId, int index, double distanceSq) {
    }

    private static ResourceKey<Level> dimension;
    private static List<Web> webs = List.of();
    @Nullable
    private static Vec3 lastPoint;
    private static Set<Item> lights = Set.of();
    private static long lightsAt = -1000;

    private WebClient() {
    }

    public static void set(ResourceKey<Level> dim, List<SyncWebsPacket.WebView> views) {
        List<Web> out = new ArrayList<>();
        for (SyncWebsPacket.WebView v : views) {
            List<Strand> strands = new ArrayList<>();
            for (SyncWebsPacket.StrandView s : v.strands()) strands.add(new Strand(s.a(), s.b(), s.broken()));
            out.add(new Web(v.id(), v.intensity(), strands));
        }
        dimension = dim;
        webs = out;
    }

    public static void onStrand(int webId, int index, boolean broken, Vec3 at) {
        for (Web w : webs) {
            if (w.id() != webId || index < 0 || index >= w.strands().size()) continue;
            w.strands().get(index).broken = broken;
        }
        Minecraft mc = Minecraft.getInstance();
        if (broken && mc.level != null) {
            for (int i = 0; i < 8; i++) {
                faygolover.zoneartifacts.client.ClientAnomalyCache.particle(mc.level, ParticleTypes.CRIT, at.x, at.y, at.z,
                        (Math.random() - 0.5) * 0.3, (Math.random() - 0.3) * 0.3, (Math.random() - 0.5) * 0.3);
            }
        }
    }

    /** The tool clicked a point (null = finished, from the air). */
    public static void onPlacerClick(@Nullable Vec3 at, boolean sneaking) {
        lastPoint = sneaking ? null : at;
    }

    private static List<Web> current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || dimension == null || !mc.level.dimension().equals(dimension)) return List.of();
        return webs;
    }

    private static boolean holdingTool(LocalPlayer player) {
        return WebPlacerItem.holds(player) || PdaItem.holds(player);
    }

    private static Set<Item> lightItems(long now) {
        if (now - lightsAt > 200) {
            lightsAt = now;
            Set<Item> out = new HashSet<>();
            for (String id : ModCommonConfig.WEB_LIGHT_ITEMS.get()) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) continue;
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null) out.add(item);
            }
            lights = out;
        }
        return lights;
    }

    // ---- aiming -------------------------------------------------------------------------------------

    /** The thread nearest along the view within reach (not behind a block). */
    @Nullable
    public static Hit pick(LocalPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        Vec3 end = eye.add(dir.scale(reach));
        double limit = reach * reach;
        HitResult blockHit = Minecraft.getInstance().hitResult;
        if (blockHit instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            limit = Math.min(limit, eye.distanceToSqr(bh.getLocation()) + 0.25);
        }
        Hit best = null;
        for (Web w : current()) {
            for (int i = 0; i < w.strands().size(); i++) {
                Strand s = w.strands().get(i);
                double[] r = closest(eye, end, s.a, s.b);
                if (r[0] > 0.3 * 0.3) continue;
                double along = r[1] * r[1] * reach * reach;
                if (along > limit) continue;
                if (best == null || along < best.distanceSq()) best = new Hit(w.id(), i, along);
            }
        }
        return best;
    }

    /** Closest approach of segments p0-p1 (the ray) and q0-q1: {distance², fraction along p}. */
    private static double[] closest(Vec3 p0, Vec3 p1, Vec3 q0, Vec3 q1) {
        Vec3 d1 = p1.subtract(p0);
        Vec3 d2 = q1.subtract(q0);
        Vec3 r = p0.subtract(q0);
        double a = d1.dot(d1);
        double e = d2.dot(d2);
        double f = d2.dot(r);
        double s;
        double t;
        if (e < 1.0E-9) {
            s = Mth.clamp(-d1.dot(r) / a, 0.0, 1.0);
            t = 0.0;
        } else {
            double c = d1.dot(r);
            double b = d1.dot(d2);
            double denom = a * e - b * b;
            s = denom > 1.0E-9 ? Mth.clamp((b * f - c * e) / denom, 0.0, 1.0) : 0.0;
            t = (b * s + f) / e;
            if (t < 0.0) {
                t = 0.0;
                s = Mth.clamp(-c / a, 0.0, 1.0);
            } else if (t > 1.0) {
                t = 1.0;
                s = Mth.clamp((b - c) / a, 0.0, 1.0);
            }
        }
        Vec3 cp = p0.add(d1.scale(s));
        Vec3 cq = q0.add(d2.scale(t));
        return new double[]{cp.distanceToSqr(cq), s};
    }

    /** Left-click with the tool: remove the thread aimed at (sneaking: its whole web). */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !WebPlacerItem.holds(player)) return;
        Hit hit = pick(player, 8.0);
        event.setCanceled(true);
        event.setSwingHand(false);
        if (hit != null) ModNetwork.CHANNEL.sendToServer(new WebEditPacket(hit.webId(), hit.index(), player.isShiftKeyDown()));
    }

    // ---- drawing ------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        List<Web> list = current();
        if (level == null || player == null) return;
        boolean tool = holdingTool(player);
        boolean placer = WebPlacerItem.holds(player);
        if (list.isEmpty() && !(placer && lastPoint != null)) return;
        float partial = event.getPartialTick();
        long now = level.getGameTime();
        float time = (now % 72000L) + partial;
        Vec3 cam = event.getCamera().getPosition();
        Set<Item> lightSet = lightItems(now);
        boolean torch = lightSet.contains(player.getMainHandItem().getItem()) || lightSet.contains(player.getOffhandItem().getItem());
        Vec3 look = player.getViewVector(partial);
        float skyDarken = level.getSkyDarken(partial);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(GlowRenderType.GLOW);
        BlockPos.MutableBlockPos lp = new BlockPos.MutableBlockPos();
        for (Web w : list) {
            float glint = Mth.clamp(w.intensity() / 3.0f, 0.3f, 2.0f);
            for (Strand s : w.strands()) {
                if (s.broken) continue;
                Vec3 mid = s.a.add(s.b).scale(0.5);
                double len = s.a.distanceTo(s.b);
                double reach = tool ? NEAR : SEEN + len * 0.5;
                if (mid.distanceToSqr(cam) > reach * reach) continue;
                int pieces = Mth.clamp((int) (len * 3.0), 4, 60);
                Vec3 axis = s.b.subtract(s.a).normalize();
                float[] alpha = new float[pieces + 1];
                Vec3[] pts = new Vec3[pieces + 1];
                for (int k = 0; k <= pieces; k++) {
                    double t = k / (double) pieces;
                    Vec3 p = s.a.add(s.b.subtract(s.a).scale(t));
                    pts[k] = p;
                    int packed = LevelRenderer.getLightColor(level, lp.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z)));
                    float light = Math.max(LightTexture.block(packed) / 15.0f, LightTexture.sky(packed) / 15.0f * skyDarken);
                    Vec3 view = p.subtract(cam).normalize();
                    double side = 1.0 - Math.abs(axis.dot(view));
                    double run = Math.pow(0.5 + 0.5 * Math.sin(time * 0.06 - len * t * 1.3 + w.id()), 14.0);
                    float a = (float) (0.015 + light * light * glint * (0.05 * side + 0.35 * run * side));
                    Vec3 to = p.subtract(player.getEyePosition(partial));
                    double d = to.length();
                    if (torch) {
                        double cos = d < 1.0E-3 ? 1.0 : to.scale(1.0 / d).dot(look);
                        if (d < SEEN && cos > 0.94) {
                            a += (float) (0.8 * glint * (cos - 0.94) / 0.06 * (1.0 - d / SEEN));
                        }
                    }
                    // Only up close: from farther off it can't be made out at all.
                    float near = (float) Mth.clamp((SEEN - d) / 2.0, 0.0, 1.0);
                    a *= near * near * (3.0f - 2.0f * near);
                    if (tool) a = Math.max(a, 0.35f);
                    alpha[k] = Mth.clamp(a, 0.0f, 1.0f);
                }
                for (int k = 0; k < pieces; k++) ribbon(vc, m, pts[k], pts[k + 1], cam, alpha[k], alpha[k + 1]);
                if (placer) {
                    dot(vc, m, s.a, cam);
                    dot(vc, m, s.b, cam);
                }
            }
        }
        // The thread being stretched.
        if (placer && lastPoint != null && mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            Vec3 to = bh.getLocation();
            int pieces = Mth.clamp((int) (lastPoint.distanceTo(to) * 4.0), 2, 128);
            for (int k = 0; k < pieces; k += 2) {
                Vec3 p0 = lastPoint.lerp(to, k / (double) pieces);
                Vec3 p1 = lastPoint.lerp(to, (k + 1) / (double) pieces);
                ribbon(vc, m, p0, p1, cam, 0.6f, 0.6f);
            }
            dot(vc, m, lastPoint, cam);
        }
        buffers.endBatch(GlowRenderType.GLOW);
        poseStack.popPose();
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 cam, float a0, float a1) {
        if (a0 <= 0.004f && a1 <= 0.004f) return;
        Vec3 side = p1.subtract(p0).cross(p0.subtract(cam));
        if (side.lengthSqr() < 1.0E-10) return;
        side = side.normalize().scale(0.008);
        put(vc, m, p0.add(side), a0);
        put(vc, m, p0.subtract(side), a0);
        put(vc, m, p1.subtract(side), a1);
        put(vc, m, p1.add(side), a1);
    }

    private static void dot(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 cam) {
        Vec3 to = cam.subtract(p).normalize();
        Vec3 up = Math.abs(to.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 r = to.cross(up).normalize().scale(0.05);
        Vec3 u = to.cross(r).normalize().scale(0.05);
        put(vc, m, p.add(r), 0.8f);
        put(vc, m, p.add(u), 0.8f);
        put(vc, m, p.subtract(r), 0.8f);
        put(vc, m, p.subtract(u), 0.8f);
    }

    private static void put(VertexConsumer vc, Matrix4f m, Vec3 p, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(232, 238, 245, (int) (255 * Mth.clamp(a, 0.0f, 1.0f))).endVertex();
    }
}
