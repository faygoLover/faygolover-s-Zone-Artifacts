package faygolover.zoneartifacts.client.fog;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AcidFog;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.client.ZoneLoopSound;
import faygolover.zoneartifacts.client.chem.Gas;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Acid Fog's look and sound: a low, dense, greenish haze made of many slow {@link Gas} puffs that
 * keeps itself topped up over the ground of the zone (faintly glowing, so it shows at night, and
 * fading by day), a quiet hissing loop, and the jets: a column of vapour and spray shooting up out
 * of the haze when the server says one went off ({@link #onJet}).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AcidFogClient {

    private static final double VISIBLE_RADIUS = 64.0;
    private static final RandomSource RANDOM = RandomSource.create();

    private static final int HAZE_DARK = 0x5C7A2E;
    private static final int HAZE_MID = 0x7A9A40;
    private static final int HAZE_LIGHT = 0x8FB050;
    private static final int JET_DARK = 0x86AE44;
    private static final int JET_LIGHT = 0xC8EC8A;
    private static final int HAZE_LIFE_MIN = 100;
    private static final int HAZE_LIFE_SPREAD = 60;
    private static final int JET_TICKS = 12;

    private static final class State {
        @Nullable
        ZoneLoopSound loop;
    }

    private static final class Jet {
        final Vec3 at;
        final int intensity;
        int ticks;

        Jet(Vec3 at, int intensity) {
            this.at = at;
            this.intensity = intensity;
        }
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();
    private static final List<Jet> JETS = new ArrayList<>();

    private AcidFogClient() {
    }

    /** A jet went off at {@code at} (ground level): the first gulp now, the rest over the next ticks. */
    public static void onJet(Vec3 at, int intensity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (at.distanceToSqr(mc.player.position()) > VISIBLE_RADIUS * VISIBLE_RADIUS * 1.6) return;
        long now = mc.level.getGameTime();
        // A dense gulp at the foot.
        for (int i = 0; i < 6; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.04, 0.05 + RANDOM.nextDouble() * 0.05, RANDOM.nextGaussian() * 0.04);
            Gas.add(new Gas.Puff(at.add(RANDOM.nextGaussian() * 0.2, 0.2, RANDOM.nextGaussian() * 0.2), v, 0.4,
                    1.0 + RANDOM.nextDouble() * 0.5, now, 25 + RANDOM.nextInt(15), 0.45f, JET_DARK, JET_LIGHT,
                    RANDOM.nextFloat() * 10f).drag(0.9).glow(0.6f));
        }
        JETS.add(new Jet(at, intensity));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            STATES.clear();
            JETS.clear();
            return;
        }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        Set<BlockPos> seen = new HashSet<>();
        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.ACID_FOG.equals(entry.typeId())) continue;
            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
            Vec3 c = zone.getCenter();
            if (c.distanceToSqr(cam) > VISIBLE_RADIUS * VISIBLE_RADIUS) continue;
            seen.add(entry.pos());
            State state = STATES.computeIfAbsent(entry.pos(), p -> new State());
            haze(level, entry, zone, now);
            if (state.loop == null || state.loop.isStopped()) {
                BlockPos pos = entry.pos();
                Double ground = Razlom.groundY(level, c.x, c.z, zone.maxY, zone.minY - 3.0);
                double y = (ground != null ? ground : zone.minY) + 0.8;
                state.loop = new ZoneLoopSound(ModSounds.FOG_IDLE.get(), new Vec3(c.x, y, c.z),
                        () -> STATES.get(pos) == state, () -> 0.45);
                mc.getSoundManager().play(state.loop);
            }
        }
        STATES.keySet().removeIf(pos -> !seen.contains(pos));

        for (Iterator<Jet> it = JETS.iterator(); it.hasNext(); ) {
            Jet jet = it.next();
            if (jet.ticks++ >= JET_TICKS) {
                it.remove();
                continue;
            }
            jet(level, jet, now);
        }
    }

    /** Keeps the haze topped up: new puffs at about the rate the old ones die, so it holds its density. */
    private static void haze(ClientLevel level, SyncAnomaliesPacket.Entry entry, AABB zone, long now) {
        int eff = ModClientConfig.effective(entry.intensity());
        double area = zone.getXsize() * zone.getZsize();
        int target = Mth.clamp((int) (area * 2.5 * eff / 3.0), 10, 160);
        double rate = target / (HAZE_LIFE_MIN + HAZE_LIFE_SPREAD * 0.5);
        int n = (int) rate + (RANDOM.nextDouble() < rate - (int) rate ? 1 : 0);
        double hazeHeight = Math.min(zone.getYsize(), AcidFog.MAX_HAZE_HEIGHT);
        for (int i = 0; i < n; i++) {
            double x = zone.minX + 0.3 + RANDOM.nextDouble() * Math.max(0.1, zone.getXsize() - 0.6);
            double z = zone.minZ + 0.3 + RANDOM.nextDouble() * Math.max(0.1, zone.getZsize() - 0.6);
            Double ground = Razlom.groundY(level, x, z, zone.maxY, zone.minY - 3.0);
            if (ground == null) continue;
            // Thicker near the ground.
            double h = hazeHeight * RANDOM.nextDouble() * RANDOM.nextDouble();
            double y = ground + 0.3 + h;
            Vec3 drift = new Vec3(RANDOM.nextGaussian() * 0.006, 0.0, RANDOM.nextGaussian() * 0.006);
            boolean dark = RANDOM.nextInt(3) == 0;
            Gas.add(new Gas.Puff(new Vec3(x, y, z), drift, 0.8, 1.6 + RANDOM.nextDouble() * 0.8, now,
                    HAZE_LIFE_MIN + RANDOM.nextInt(HAZE_LIFE_SPREAD), 0.14f + RANDOM.nextFloat() * 0.06f,
                    dark ? HAZE_DARK : HAZE_MID, HAZE_LIGHT, RANDOM.nextFloat() * 10f)
                    .settle(y).drag(0.98).glow(0.45f).dayFade());
        }
    }

    /** One tick of a jet: vapour shooting up in a column, and spray thrown up and falling back. */
    private static void jet(ClientLevel level, Jet jet, long now) {
        int eff = ModClientConfig.effective(jet.intensity);
        float fade = 1.0f - jet.ticks / (float) JET_TICKS;
        int puffs = 2 + eff / 3;
        for (int i = 0; i < puffs; i++) {
            double up = 0.25 + RANDOM.nextDouble() * 0.15;
            Vec3 p = jet.at.add(RANDOM.nextGaussian() * AcidFog.JET_RADIUS * 0.25, 0.1, RANDOM.nextGaussian() * AcidFog.JET_RADIUS * 0.25);
            Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.015, up, RANDOM.nextGaussian() * 0.015);
            Gas.add(new Gas.Puff(p, v, 0.3, 0.9 + RANDOM.nextDouble() * 0.6, now, 30 + RANDOM.nextInt(20),
                    (0.3f + 0.15f * fade), JET_DARK, JET_LIGHT, RANDOM.nextFloat() * 10f).drag(0.93).glow(0.6f));
        }
        int drops = (int) ((3 + eff) * fade);
        for (int i = 0; i < drops; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0;
            double s = 0.03 + RANDOM.nextDouble() * 0.06;
            level.addParticle(ModParticles.CHEM_DROP.get(),
                    jet.at.x + RANDOM.nextGaussian() * 0.2, jet.at.y + 0.2, jet.at.z + RANDOM.nextGaussian() * 0.2,
                    Math.cos(a) * s, 0.35 + RANDOM.nextDouble() * 0.3, Math.sin(a) * s);
        }
    }
}
