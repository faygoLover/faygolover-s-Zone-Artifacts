package faygolover.zoneartifacts.client.thermal;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Thermal;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client side of Zharka and Iney: the activity ramp (0..1, up in 0.5 s, down in 3 s, driven by the
 * synced {@code active} flag), the cached block faces inside each zone, particles and sounds.
 * The frost crystals on Iney's faces are drawn by {@link IneyFrostRenderer} from the same state.
 * <ul>
 *     <li><b>Zharka</b> — long-lived sparks and fewer smoke wisps, mostly lifting off block faces,
 *     rarely in the air; idle they barely drift up, active there are more and they rise fast.
 *     A looping fire hum whose volume follows the activity.</li>
 *     <li><b>Iney</b> — pale mist creeping over the faces and a few snowflakes; more of both when
 *     active. Now and then a faint icy crackle (vanilla-backed placeholder), more often when active.</li>
 * </ul>
 * Particle counts scale with the effective intensity (the anomaly's own, capped by the player's
 * {@code maxEffectIntensity}) and the zone's size.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThermalClientHandler {

    /** Anomalies farther than this from the camera get no particles, sounds or crystals. */
    public static final double VISIBLE_RADIUS = 40.0;

    private static final int FACE_REFRESH_TICKS = 40;
    /** Share of particles spawned in the open air instead of on a face. */
    private static final float AIR_SHARE = 0.15f;

    private static final Map<Key, State> STATES = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ThermalClientHandler() {
    }

    public record Key(ResourceLocation typeId, BlockPos pos) {
    }

    /** A full block face inside a zone: its center and the direction it faces. */
    public record Face(Vec3 center, Direction direction) {
    }

    /** Everything the client keeps about one thermal anomaly. */
    public static final class State {
        private SyncAnomaliesPacket.Entry entry;
        private float activity;
        private float previousActivity;
        private List<Face> faces = List.of();
        private float scannedSize = -1.0f;
        private int nextScan;
        private long facesVersion;
        @Nullable
        private ThermalLoopSound loop;

        public SyncAnomaliesPacket.Entry entry() {
            return entry;
        }

        public float activity(float partialTick) {
            return Mth.lerp(partialTick, previousActivity, activity);
        }

        public List<Face> faces() {
            return faces;
        }

        /** Changes whenever {@link #faces()} is rebuilt, so dependent caches know to rebuild too. */
        public long facesVersion() {
            return facesVersion;
        }

        public boolean isZharka() {
            return AnomalyTypeIds.ZHARKA.equals(entry.typeId());
        }
    }

    /** Current states, for the renderer. */
    public static Collection<Map.Entry<Key, State>> states() {
        return STATES.entrySet();
    }

    /** Current activity of one anomaly (0 if unknown), used by its loop sound. */
    public static float activity(Key key) {
        State state = STATES.get(key);
        return state == null ? 0.0f : state.activity;
    }

    public static boolean isTracked(Key key) {
        return STATES.containsKey(key);
    }

    // ==== tick =====================================================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            clear(mc);
            return;
        }
        if (mc.isPaused()) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double radiusSq = VISIBLE_RADIUS * VISIBLE_RADIUS;
        Set<Key> seen = new HashSet<>();

        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.isThermal(entry.typeId())) continue;
            if (Vec3.atCenterOf(entry.pos()).distanceToSqr(cam) > radiusSq) continue;

            Key key = new Key(entry.typeId(), entry.pos());
            seen.add(key);
            State state = STATES.computeIfAbsent(key, k -> new State());
            state.entry = entry;

            state.previousActivity = state.activity;
            state.activity = entry.active()
                    ? Math.min(1.0f, state.activity + Thermal.ACTIVITY_UP_PER_TICK)
                    : Math.max(0.0f, state.activity - Thermal.ACTIVITY_DOWN_PER_TICK);

            AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
            if (--state.nextScan <= 0 || state.scannedSize != entry.size()) {
                state.faces = scanFaces(level, zone);
                state.scannedSize = entry.size();
                state.nextScan = FACE_REFRESH_TICKS + RANDOM.nextInt(10);
                state.facesVersion++;
            }

            if (state.isZharka()) {
                tickZharka(mc, level, key, state, zone);
            } else {
                tickIney(level, state, zone);
            }
        }

        for (Iterator<Map.Entry<Key, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, State> e = it.next();
            if (seen.contains(e.getKey())) continue;
            stopLoop(mc, e.getValue());
            it.remove();
        }
    }

    private static void clear(Minecraft mc) {
        for (State state : STATES.values()) stopLoop(mc, state);
        STATES.clear();
    }

    private static void stopLoop(Minecraft mc, State state) {
        if (state.loop != null) {
            mc.getSoundManager().stop(state.loop);
            state.loop = null;
        }
    }

    // ---- Zharka -------------------------------------------------------------------------

    private static void tickZharka(Minecraft mc, ClientLevel level, Key key, State state, AABB zone) {
        float a = state.activity;
        double rate = baseRate(state.entry) * 0.035 * (1.0 + 3.0 * a);

        int embers = roll(rate);
        for (int i = 0; i < embers; i++) {
            Vec3 p = spawnPoint(state, zone, 0.03);
            double up = Mth.lerp(a, 0.004, 0.035) + RANDOM.nextDouble() * Mth.lerp(a, 0.004, 0.02);
            double side = Mth.lerp(a, 0.002, 0.008);
            level.addParticle(ModParticles.EMBER.get(), p.x, p.y, p.z,
                    (RANDOM.nextDouble() - 0.5) * 2 * side, up, (RANDOM.nextDouble() - 0.5) * 2 * side);
        }

        int smokes = roll(rate * 0.3);
        for (int i = 0; i < smokes; i++) {
            Vec3 p = spawnPoint(state, zone, 0.06);
            double up = Mth.lerp(a, 0.003, 0.025) + RANDOM.nextDouble() * Mth.lerp(a, 0.003, 0.012);
            double side = Mth.lerp(a, 0.0015, 0.006);
            level.addParticle(ModParticles.HEAT_SMOKE.get(), p.x, p.y, p.z,
                    (RANDOM.nextDouble() - 0.5) * 2 * side, up, (RANDOM.nextDouble() - 0.5) * 2 * side);
        }

        // Looping hum; its volume follows the activity (see ThermalLoopSound).
        if (state.loop == null || state.loop.isStopped()) {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(Thermal.ZHARKA_IDLE_SOUND);
            if (sound != null) {
                Vec3 c = zone.getCenter();
                state.loop = new ThermalLoopSound(key, sound, c, Thermal.ZHARKA_IDLE_VOLUME, Thermal.ZHARKA_ACTIVE_VOLUME);
                mc.getSoundManager().play(state.loop);
            }
        }
    }

    // ---- Iney ---------------------------------------------------------------------------

    private static void tickIney(ClientLevel level, State state, AABB zone) {
        float a = state.activity;
        double base = baseRate(state.entry);

        int mists = roll(base * 0.03 * (1.0 + 2.0 * a));
        for (int i = 0; i < mists; i++) {
            Vec3 p = spawnPoint(state, zone, 0.08);
            double side = Mth.lerp(a, 0.003, 0.012);
            level.addParticle(ModParticles.FROST_MIST.get(), p.x, p.y, p.z,
                    (RANDOM.nextDouble() - 0.5) * 2 * side, (RANDOM.nextDouble() - 0.6) * 0.004, (RANDOM.nextDouble() - 0.5) * 2 * side);
        }

        int flakes = roll(base * 0.015 * (1.0 + 4.0 * a));
        for (int i = 0; i < flakes; i++) {
            Vec3 p = randomInside(zone);
            level.addParticle(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 0.0, -0.01, 0.0);
        }

        // Occasional icy crackle from a random spot of the zone.
        float chance = Mth.lerp(a, 1.0f / 120.0f, 1.0f / 30.0f);
        if (RANDOM.nextFloat() < chance) {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(Thermal.INEY_IDLE_SOUND);
            if (sound != null) {
                Vec3 p = spawnPoint(state, zone, 0.0);
                float volume = Mth.lerp(a, Thermal.INEY_IDLE_VOLUME, Thermal.INEY_ACTIVE_VOLUME);
                level.playLocalSound(p.x, p.y, p.z, sound, SoundSource.AMBIENT, volume, 0.8f + RANDOM.nextFloat() * 0.5f, false);
            }
        }
    }

    // ---- shared helpers -------------------------------------------------------------------

    /** Effective intensity times a surface-area factor for bigger zones. */
    private static double baseRate(SyncAnomaliesPacket.Entry entry) {
        double size = Math.max(1.0, entry.size());
        return ModClientConfig.effective(entry.intensity()) * Math.min(30.0, size * size);
    }

    /** Whole part of {@code rate}, plus one more with the fractional part as the chance. */
    private static int roll(double rate) {
        int n = (int) rate;
        if (RANDOM.nextDouble() < rate - n) n++;
        return n;
    }

    /** Mostly a random point on one of the zone's block faces, lifted {@code lift} off it;
     *  sometimes (or when there are no faces) a random point in the air of the zone. */
    private static Vec3 spawnPoint(State state, AABB zone, double lift) {
        List<Face> faces = state.faces;
        if (faces.isEmpty() || RANDOM.nextFloat() < AIR_SHARE) return randomInside(zone);
        Face face = faces.get(RANDOM.nextInt(faces.size()));
        Vec3 p = pointOnFace(face, (RANDOM.nextDouble() - 0.5) * 0.9, (RANDOM.nextDouble() - 0.5) * 0.9, lift);
        return zone.inflate(0.1).contains(p) ? p : randomInside(zone);
    }

    private static Vec3 randomInside(AABB zone) {
        return new Vec3(Mth.lerp(RANDOM.nextDouble(), zone.minX, zone.maxX),
                Mth.lerp(RANDOM.nextDouble(), zone.minY, zone.maxY),
                Mth.lerp(RANDOM.nextDouble(), zone.minZ, zone.maxZ));
    }

    /** Tangent of a face: X for horizontal faces, Y for walls. The second in-plane axis is
     *  {@code normal × tangent}, which keeps quads built from them counter-clockwise seen from outside. */
    public static Vec3 tangent(Direction direction) {
        return direction.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
    }

    public static Vec3 normal(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public static Vec3 pointOnFace(Face face, double u, double v, double lift) {
        Vec3 n = normal(face.direction());
        Vec3 t = tangent(face.direction());
        Vec3 b = n.cross(t);
        return face.center().add(t.scale(u)).add(b.scale(v)).add(n.scale(lift));
    }

    /**
     * Full block faces around which the anomaly shows: faces of blocks with a full sturdy side,
     * not covered by an opaque neighbour, whose center lies inside the zone (a hair of tolerance,
     * so the floor right under a one-block zone counts).
     */
    private static List<Face> scanFaces(ClientLevel level, AABB zone) {
        AABB accept = zone.inflate(0.05);
        int x0 = Mth.floor(zone.minX) - 1, y0 = Mth.floor(zone.minY) - 1, z0 = Mth.floor(zone.minZ) - 1;
        int x1 = Mth.floor(zone.maxX), y1 = Mth.floor(zone.maxY), z1 = Mth.floor(zone.maxZ);
        List<Face> faces = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    for (Direction dir : Direction.values()) {
                        double cx = x + 0.5 + dir.getStepX() * 0.5;
                        double cy = y + 0.5 + dir.getStepY() * 0.5;
                        double cz = z + 0.5 + dir.getStepZ() * 0.5;
                        if (!accept.contains(cx, cy, cz)) continue;
                        if (!state.isFaceSturdy(level, pos, dir)) continue;
                        next.setWithOffset(pos, dir);
                        if (level.getBlockState(next).isSolidRender(level, next)) continue;
                        faces.add(new Face(new Vec3(cx, cy, cz), dir));
                    }
                }
            }
        }
        return faces;
    }
}
