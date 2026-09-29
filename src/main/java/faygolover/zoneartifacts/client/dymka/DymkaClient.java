package faygolover.zoneartifacts.client.dymka;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.chem.Gas;
import faygolover.zoneartifacts.client.fx.HumLoop;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Haze: from outside a faint grey mist hanging in the zone; inside, the fog closes in to a few
 * blocks ({@code ScreenFx}), sounds come muffled and a little lower ({@code SoundFx}), and now and
 * then something is heard far off — a knock, a groan of metal, a voice-like whine — from nowhere in
 * particular.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DymkaClient {

    private static final RandomSource RANDOM = RandomSource.create();
    private static final double NEAR = 96.0;

    private static float inside;
    private static float prevInside;
    private static float visibility = 5.0f;
    private static float fogLight = 1.0f;
    private static boolean present;
    private static int nextDistant = 100;

    private DymkaClient() {
    }

    public static boolean present() {
        return present;
    }

    /** 0..1: how deep in a Haze the eyes are. */
    public static float inside(float partial) {
        return Mth.lerp(partial, prevInside, inside);
    }

    /** How far one sees deep inside, blocks. */
    public static float visibility() {
        return visibility;
    }

    /** The fog's brightness (it is grey by day, dark at night). */
    public static float fogLight() {
        return fogLight;
    }

    public static float hearing() {
        return 1.0f - 0.65f * inside;
    }

    public static float pitch() {
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level == null ? 0.0f : (mc.level.getGameTime() % 72000L);
        return 1.0f - inside * (0.1f + 0.025f * Mth.sin(time * 0.05f));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        prevInside = inside;
        if (level == null || mc.player == null) {
            inside = 0.0f;
            present = false;
            return;
        }
        if (mc.isPaused()) return;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        long now = level.getGameTime();
        float target = 0.0f;
        float vis = 64.0f;
        boolean any = false;
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.DYMKA.equals(entry.typeId())) continue;
            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
            if (zone.getCenter().distanceTo(eye) > NEAR + entry.size()) continue;
            any = true;
            float k = HumLoop.depthInside(zone, eye, 2.0);
            if (k > target) {
                target = k;
                vis = (float) Math.max(1.5, ModCommonConfig.DYMKA_VISIBILITY.get() * 3.0 / Math.max(1, entry.intensity()));
            }
            mist(level, entry, zone, eye, now);
        }
        present = any;
        inside += Mth.clamp(target - inside, -0.05f, 0.05f);
        if (target > 0.0f) visibility = vis;

        int packed = LevelRenderer.getLightColor(level, BlockPos.containing(eye));
        float sky = LightTexture.sky(packed) / 15.0f * level.getSkyDarken(1.0f);
        float block = LightTexture.block(packed) / 15.0f;
        fogLight = 0.2f + 0.8f * Math.max(sky, block * 0.7f);

        if (inside > 0.5f) {
            if (--nextDistant <= 0) {
                distant(mc, eye);
                double min = ModCommonConfig.DYMKA_SOUND_MIN_SECONDS.get();
                double max = Math.max(min, ModCommonConfig.DYMKA_SOUND_MAX_SECONDS.get());
                nextDistant = (int) ((min + RANDOM.nextDouble() * (max - min)) * 20.0);
            }
        } else if (nextDistant < 60) {
            nextDistant = 60 + RANDOM.nextInt(100);
        }
    }

    /** From outside: a faint grey mist hanging in the zone. */
    private static void mist(ClientLevel level, SyncAnomaliesPacket.Entry entry, AABB zone, Vec3 eye, long now) {
        int eff = ModClientConfig.effective(entry.intensity());
        double volume = zone.getXsize() * zone.getYsize() * zone.getZsize();
        double rate = Math.min(3.0, volume * 0.004 * eff / 3.0);
        int n = (int) rate + (RANDOM.nextDouble() < rate - (int) rate ? 1 : 0);
        for (int i = 0; i < n; i++) {
            Vec3 p = new Vec3(Mth.lerp(RANDOM.nextDouble(), zone.minX + 0.5, zone.maxX - 0.5),
                    Mth.lerp(RANDOM.nextDouble(), zone.minY + 0.3, zone.maxY - 0.5),
                    Mth.lerp(RANDOM.nextDouble(), zone.minZ + 0.5, zone.maxZ - 0.5));
            if (p.distanceToSqr(eye) < 4.0) continue;
            Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.004, 0.0, RANDOM.nextGaussian() * 0.004);
            Gas.add(new Gas.Puff(p, v, 1.0, 1.8 + RANDOM.nextDouble(), now, 140 + RANDOM.nextInt(80),
                    0.07f + RANDOM.nextFloat() * 0.04f, 0xB9BEC2, 0xD4D8DB, RANDOM.nextFloat() * 10f).settle(p.y).drag(0.99));
        }
    }

    /** Something far away, from no telling where. */
    private static void distant(Minecraft mc, Vec3 eye) {
        double a = RANDOM.nextDouble() * Math.PI * 2.0;
        double d = 15.0 + RANDOM.nextDouble() * 15.0;
        Vec3 at = eye.add(Math.cos(a) * d, (RANDOM.nextDouble() - 0.3) * 6.0, Math.sin(a) * d);
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.DYMKA_DISTANT.get(), SoundSource.AMBIENT,
                0.7f + RANDOM.nextFloat() * 0.3f, 0.8f + RANDOM.nextFloat() * 0.35f, RANDOM,
                at.x, at.y, at.z));
    }
}
