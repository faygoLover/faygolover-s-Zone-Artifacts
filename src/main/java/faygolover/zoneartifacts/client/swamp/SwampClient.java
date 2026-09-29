package faygolover.zoneartifacts.client.swamp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Swamp;
import faygolover.zoneartifacts.anomaly.SwampPhysics;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The swamp on the client: the own player's sinking ({@link SwampPhysics}, players move themselves),
 * the dark when one's head goes under (see {@link #darkness}), slow rings running over the surface
 * from whatever moves on it or falls onto it, the surface's faint sheen, and silence under foot (step
 * sounds on it are dropped).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SwampClient {

    private static final Map<LivingEntity, SwampPhysics.State> STATES = new WeakHashMap<>();
    private static final Map<Entity, Long> LAST_RIPPLE = new WeakHashMap<>();
    private static final Map<Entity, Boolean> WAS_ON_GROUND = new WeakHashMap<>();
    private static final int RIPPLE_LIFE = 70;
    private static final int SEGMENTS = 40;

    private record Ripple(Vec3 at, long born, float strength, Swamp.Columns cols) {
    }

    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static float darkness;
    private static float prevDarkness;

    private SwampClient() {
    }

    /** 0..1: how much of the view the mud has swallowed (the head under the surface). */
    public static float darkness(float partial) {
        return Mth.lerp(partial, prevDarkness, darkness);
    }

    /** Sounds are muffled under the mud: volume factor. */
    public static float hearing() {
        return 1.0f - 0.75f * darkness;
    }

    public static List<SwampPhysics.Zone> zones(ClientLevel level) {
        List<SwampPhysics.Zone> out = new ArrayList<>();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (AnomalyTypeIds.SWAMP.equals(entry.typeId())) {
                out.add(new SwampPhysics.Zone(entry.pos(), entry.size(), entry.speed(), 0.0f, 1.0));
            }
        }
        return out;
    }

    // ---- the own player's movement ----------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.level().isClientSide || e != Minecraft.getInstance().player) return;
        List<SwampPhysics.Zone> zones = zones((ClientLevel) e.level());
        if (zones.isEmpty() && STATES.isEmpty()) return;
        SwampPhysics.begin(e, zones, STATES);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isClient() || event.player != Minecraft.getInstance().player) return;
        SwampPhysics.State s = STATES.get(event.player);
        if (s != null) SwampPhysics.finish(event.player, s);
    }

    // ---- darkness, rings --------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        prevDarkness = darkness;
        if (level == null || mc.player == null) {
            RIPPLES.clear();
            STATES.clear();
            darkness = 0.0f;
            return;
        }
        if (mc.isPaused()) return;
        List<SwampPhysics.Zone> zones = zones(level);
        if (zones.isEmpty()) {
            RIPPLES.clear();
            darkness = Math.max(0.0f, darkness - 0.2f);
            return;
        }
        long now = level.getGameTime();
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        float target = 0.0f;
        for (SwampPhysics.Zone zone : zones) {
            if (!zone.pos().closerThan(mc.player.blockPosition(), zone.size() + 64.0)) continue;
            Swamp.Columns cols = Swamp.columns(level, zone.pos(), zone.size());
            if (cols.liquefied(eye.x, eye.y, eye.z)) {
                target = 1.0f;
            } else if (cols.depthAt(eye.x, eye.z) > 0 && eye.y >= cols.surface() && eye.y < cols.surface() + 0.3
                    && STATES.containsKey(mc.player)) {
                target = Math.max(target, (float) (1.0 - (eye.y - cols.surface()) / 0.3) * 0.9f);
            }
            ripples(level, cols, now);
        }
        darkness += Mth.clamp(target - darkness, -0.15f, 0.25f);
        RIPPLES.removeIf(r -> now - r.born() > RIPPLE_LIFE);
    }

    /** New rings from whatever moves on the surface or lands on it. */
    private static void ripples(ClientLevel level, Swamp.Columns cols, long now) {
        AABB area = cols.region.inflate(0.0, 1.0, 0.0);
        double surface = cols.surface();
        for (Entity e : level.getEntities((Entity) null, area, x -> x instanceof LivingEntity || x instanceof ItemEntity || x instanceof Projectile)) {
            if (cols.depthAt(e.getX(), e.getZ()) <= 0) continue;
            if (e.getY() > surface + 0.7 || e.getY() < surface - 2.5) continue;
            Long last = LAST_RIPPLE.get(e);
            if (e instanceof LivingEntity) {
                double moved = new Vec3(e.getX() - e.xo, 0.0, e.getZ() - e.zo).length();
                if (moved < 0.015 || (last != null && now - last < 12)) continue;
                LAST_RIPPLE.put(e, now);
                add(new Vec3(e.getX(), surface, e.getZ()), now, (float) Math.min(1.0, 0.4 + moved * 4.0), cols);
            } else {
                // Something light only rings once when it lands (it stays lying on the surface).
                boolean ground = e.onGround() || (e instanceof Projectile && e.getDeltaMovement().lengthSqr() < 1.0E-4);
                Boolean was = WAS_ON_GROUND.put(e, ground);
                if (ground && (was == null || !was)) add(new Vec3(e.getX(), surface, e.getZ()), now, 0.3f, cols);
            }
        }
    }

    private static void add(Vec3 at, long now, float strength, Swamp.Columns cols) {
        if (RIPPLES.size() > 64) RIPPLES.remove(0);
        RIPPLES.add(new Ripple(at, now, strength, cols));
    }

    // ---- silence under foot -------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) return;
        String path = sound.getLocation().getPath();
        if (!path.endsWith(".step")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (SwampPhysics.Zone zone : zones(mc.level)) {
            Swamp.Columns cols = Swamp.columns(mc.level, zone.pos(), zone.size());
            if (cols.depthAt(sound.getX(), sound.getZ()) > 0 && sound.getY() < cols.surface() + 1.5 && sound.getY() > cols.surface() - 3.0) {
                event.setSound(null);
                return;
            }
        }
    }

    // ---- drawing --------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        List<SwampPhysics.Zone> zones = zones(level);
        if (zones.isEmpty()) return;
        float partial = event.getPartialTick();
        long now = level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(false);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (SwampPhysics.Zone zone : zones) {
            if (Vec3.atCenterOf(zone.pos()).distanceToSqr(cam) > 56.0 * 56.0) continue;
            sheen(buffer, m, Swamp.columns(level, zone.pos(), zone.size()), cam);
        }
        for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext(); ) {
            Ripple r = it.next();
            float age = (now - r.born() + partial) / RIPPLE_LIFE;
            if (age > 1.0f) continue;
            ring(buffer, m, r, age);
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** A barely-there gloss on the surface, stronger at grazing angles (too smooth for ground). */
    private static void sheen(BufferBuilder buffer, Matrix4f m, Swamp.Columns cols, Vec3 cam) {
        double y = cols.surface() + 0.004;
        if (cam.y < y) return;
        AABB r = cols.region;
        for (int x = Mth.floor(r.minX); x <= Mth.floor(r.maxX - 1.0E-6); x++) {
            for (int z = Mth.floor(r.minZ); z <= Mth.floor(r.maxZ - 1.0E-6); z++) {
                if (cols.depthAt(x, z) <= 0) continue;
                sheenVertex(buffer, m, x, y, z, cam);
                sheenVertex(buffer, m, x, y, z + 1, cam);
                sheenVertex(buffer, m, x + 1, y, z + 1, cam);
                sheenVertex(buffer, m, x + 1, y, z, cam);
            }
        }
    }

    private static void sheenVertex(BufferBuilder buffer, Matrix4f m, double x, double y, double z, Vec3 cam) {
        Vec3 to = new Vec3(x - cam.x, y - cam.y, z - cam.z);
        double len = to.length();
        double grazing = len < 1.0E-4 ? 0.0 : 1.0 - Math.abs(to.y / len);
        int a = (int) (255 * 0.07 * grazing * grazing * grazing);
        buffer.vertex(m, (float) x, (float) y, (float) z).color(210, 220, 230, a).endVertex();
    }

    /** A slow ring: a pale crest and a dark trough just inside it, only over the swamp. */
    private static void ring(BufferBuilder buffer, Matrix4f m, Ripple r, float age) {
        double radius = 0.15 + age * 2.4 * (0.6 + 0.4 * r.strength());
        double width = 0.06 + 0.05 * age;
        float fade = (1.0f - age) * (1.0f - age) * r.strength();
        double y = r.cols().surface() + 0.006;
        band(buffer, m, r, radius, radius + width, y, 235, 240, 245, 0.16f * fade);
        band(buffer, m, r, Math.max(0.0, radius - width * 1.5), radius, y, 20, 24, 20, 0.12f * fade);
    }

    private static void band(BufferBuilder buffer, Matrix4f m, Ripple r, double r0, double r1, double y,
                             int red, int green, int blue, float alpha) {
        if (alpha <= 0.004f) return;
        double mid = (r0 + r1) * 0.5;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
            double cx0 = r.at().x + Math.cos(a0) * mid;
            double cz0 = r.at().z + Math.sin(a0) * mid;
            double cx1 = r.at().x + Math.cos(a1) * mid;
            double cz1 = r.at().z + Math.sin(a1) * mid;
            int k0 = r.cols().depthAt(cx0, cz0) > 0 ? (int) (255 * alpha) : 0;
            int k1 = r.cols().depthAt(cx1, cz1) > 0 ? (int) (255 * alpha) : 0;
            if (k0 == 0 && k1 == 0) continue;
            put(buffer, m, r.at().x + Math.cos(a0) * r0, y, r.at().z + Math.sin(a0) * r0, red, green, blue, 0);
            put(buffer, m, cx0, y, cz0, red, green, blue, k0);
            put(buffer, m, cx1, y, cz1, red, green, blue, k1);
            put(buffer, m, r.at().x + Math.cos(a1) * r0, y, r.at().z + Math.sin(a1) * r0, red, green, blue, 0);
            put(buffer, m, cx0, y, cz0, red, green, blue, k0);
            put(buffer, m, r.at().x + Math.cos(a0) * r1, y, r.at().z + Math.sin(a0) * r1, red, green, blue, 0);
            put(buffer, m, r.at().x + Math.cos(a1) * r1, y, r.at().z + Math.sin(a1) * r1, red, green, blue, 0);
            put(buffer, m, cx1, y, cz1, red, green, blue, k1);
        }
    }

    private static void put(BufferBuilder buffer, Matrix4f m, double x, double y, double z, int r, int g, int b, int a) {
        buffer.vertex(m, (float) x, (float) y, (float) z).color(r, g, b, a).endVertex();
    }
}
