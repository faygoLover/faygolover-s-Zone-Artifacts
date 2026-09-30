package faygolover.zoneartifacts.client.sumrak;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.chem.Gas;
import faygolover.zoneartifacts.client.fx.HumLoop;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Dusk: a volume of churning darkness (black {@link Gas} puffs, their edges curling out and back
 * like smoke). Whatever is inside is not drawn for anyone looking from outside (so it can't show
 * through the gaps), and no sound from inside reaches out; inside, black fog at arm's length and a
 * low drone in place of every other sound.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SumrakClient {

    private static final double NEAR = 96.0;
    private static final int INNER = 0x030304;
    private static final int OUTER = 0x0A0A0E;

    /** How far inside the edge something must be before it's gone: first it sinks into the dark. */
    private static final double SWALLOW = 0.6;

    private static final List<AABB> ZONES = new ArrayList<>();
    /** Hidden this frame and marked invisible (so vanilla drops their shadows too), to put back after. */
    private static final List<Entity> MASKED = new ArrayList<>();
    private static final List<Integer> DENSITY = new ArrayList<>();
    private static float inside;
    private static float prevInside;
    @Nullable
    private static AABB cameraZone;
    @Nullable
    private static HumLoop drone;

    static {
        Gas.addFrameSource(SumrakClient::puffs);
    }

    private SumrakClient() {
    }

    public static boolean present() {
        return !ZONES.isEmpty();
    }

    public static float inside(float partial) {
        return Mth.lerp(partial, prevInside, inside);
    }

    /** Brightness left for glows seen from inside (they ignore fog: the Moon, comets…). */
    public static float glowLight() {
        return 1.0f - 0.97f * Mth.clamp(inside, 0.0f, 1.0f);
    }

    /** Volume factor for a sound: from inside nearly nothing is heard; from inside out, nothing. */
    public static float hearing(boolean relative, double x, double y, double z) {
        float v = 1.0f - 0.95f * inside;
        if (relative || ZONES.isEmpty()) return v;
        for (AABB zone : ZONES) {
            if (zone.contains(x, y, z) && zone != cameraZone) return v * 0.05f;
        }
        return v;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        prevInside = inside;
        ZONES.clear();
        DENSITY.clear();
        cameraZone = null;
        if (level == null || mc.player == null) {
            inside = 0.0f;
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        float target = 0.0f;
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.SUMRAK.equals(entry.typeId())) continue;
            AABB zone = AnomalyGeometry.box(entry);
            if (zone.getCenter().distanceTo(eye) > NEAR + entry.size()) continue;
            ZONES.add(zone);
            DENSITY.add(ModClientConfig.effective(entry.intensity()));
            float k = HumLoop.depthInside(zone, eye, 1.0);
            if (zone.contains(eye)) cameraZone = zone;
            target = Math.max(target, k);
        }
        if (!mc.isPaused()) inside += Mth.clamp(target - inside, -0.08f, 0.08f);
        if (inside > 0.05f && (drone == null || drone.isStopped())) {
            drone = new HumLoop(ModSounds.SUMRAK_DRONE.get(), () -> 0.55 * inside);
            mc.getSoundManager().play(drone);
        }
    }

    /** Those deep enough inside can't be seen from outside — nor their shadows. */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        Entity e = event.getEntity();
        if (!hidden(e)) return;
        event.setCanceled(true);
        // Vanilla draws the shadow right after, unless the entity is invisible.
        if (!e.isInvisible()) {
            e.setInvisible(true);
            MASKED.add(e);
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || MASKED.isEmpty()) return;
        for (Entity e : MASKED) e.setInvisible(false);
        MASKED.clear();
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (hidden(event.getEntity())) event.setResult(Event.Result.DENY);
    }

    private static boolean hidden(Entity entity) {
        if (ZONES.isEmpty()) return false;
        AABB box = entity.getBoundingBox();
        for (AABB zone : ZONES) {
            if (zone == cameraZone) continue;
            AABB inner = zone.deflate(SWALLOW);
            if (box.minX >= inner.minX && box.maxX <= inner.maxX && box.minY >= inner.minY && box.maxY <= inner.maxY
                    && box.minZ >= inner.minZ && box.maxZ <= inner.maxZ) return true;
        }
        return false;
    }

    /** The darkness itself: puffs on a loose grid through the zone, rolling slowly; the outer ones
     *  curl out past the edge and back like smoke. */
    private static void puffs(List<Gas.FramePuff> out) {
        if (ZONES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float time = (mc.level.getGameTime() % 72000L) + mc.getFrameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (int zi = 0; zi < ZONES.size(); zi++) {
            AABB zone = ZONES.get(zi);
            int eff = DENSITY.get(zi);
            double volume = zone.getXsize() * zone.getYsize() * zone.getZsize();
            double step = Math.max(1.1, Math.cbrt(volume / 380.0));
            float alpha = Mth.clamp(0.82f + 0.04f * eff, 0.82f, 0.97f);
            int nx = Math.max(1, (int) Math.round(zone.getXsize() / step));
            int ny = Math.max(1, (int) Math.round(zone.getYsize() / step));
            int nz = Math.max(1, (int) Math.round(zone.getZsize() / step));
            RandomSource shape = RandomSource.create((long) (zone.minX * 31 + zone.minZ * 17 + zone.minY));
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < ny; j++) {
                    for (int k = 0; k < nz; k++) {
                        float seed = shape.nextFloat() * 100.0f;
                        double fx = (i + 0.5) / nx;
                        double fy = (j + 0.5) / ny;
                        double fz = (k + 0.5) / nz;
                        boolean edge = i == 0 || j == 0 || k == 0 || i == nx - 1 || j == ny - 1 || k == nz - 1;
                        double roll = 0.25 * step;
                        double x = Mth.lerp(fx, zone.minX, zone.maxX) + roll * Math.sin(time * 0.013 + seed);
                        double y = Mth.lerp(fy, zone.minY, zone.maxY) + roll * Math.sin(time * 0.011 + seed * 1.3);
                        double z = Mth.lerp(fz, zone.minZ, zone.maxZ) + roll * Math.cos(time * 0.012 + seed * 0.7);
                        Vec3 p = new Vec3(x, y, z);
                        if (edge) {
                            // Curling out past the edge and back.
                            Vec3 c = zone.getCenter();
                            Vec3 outward = p.subtract(c).normalize();
                            double curl = Math.max(0.0, Math.sin(time * 0.02 + seed * 2.1)) * 0.5 * step;
                            p = p.add(outward.scale(curl));
                        }
                        if (p.distanceToSqr(cam) < 0.8) continue;
                        double r = step * (0.92 + 0.12 * Math.sin(time * 0.017 + seed));
                        out.add(new Gas.FramePuff(p, r, edge ? alpha * 0.88f : alpha, INNER, OUTER, seed));
                    }
                }
            }
        }
    }

    /** Inside, no sky at all: the Moon and the stars are drawn without fog and would shine through. */
    @Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Sky {

        private Sky() {
        }

        @SubscribeEvent
        public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new DimensionSpecialEffects.OverworldEffects() {
                @Override
                public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                                         Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
                    return inside(partialTick) > 0.5f;
                }
            });
        }
    }
}
