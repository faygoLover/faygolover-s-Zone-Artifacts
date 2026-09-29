package faygolover.zoneartifacts.client.razlom;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
 * Client side of the Razlom: the crack pattern (seeded from the position, so it never changes
 * shape; heights re-read from the terrain every few seconds), the flame's position, particles,
 * the quiet hum and the jet (target + sound) from {@link faygolover.zoneartifacts.network.RazlomJetPacket}.
 * Drawn by {@link RazlomRenderer}.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RazlomClientHandler {

    public static final double VISIBLE_RADIUS = 48.0;
    private static final int RESCAN_TICKS = 60;
    private static final double STEP = 0.2;

    private static final Map<BlockPos, State> STATES = new HashMap<>();
    /** Jets reported before the anomaly came into range are kept here until it does. */
    private static final Map<BlockPos, Jet> PENDING_JETS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private RazlomClientHandler() {
    }

    /** One crack: a polyline over the ground. {@code ys[i]} is null where there is no ground.
     *  A branch starts on its main crack, so only its far end is a free end. */
    public static final class Crack {
        public final double[] xs;
        public final double[] zs;
        public final float[] widths;
        public final Double[] ys;
        public final boolean branch;

        Crack(double[] xs, double[] zs, float[] widths, boolean branch) {
            this.xs = xs;
            this.zs = zs;
            this.widths = widths;
            this.branch = branch;
            this.ys = new Double[xs.length];
        }

        /** The glowing seam stops one segment short of every free end. */
        public boolean seamCovers(int segment) {
            if (segment >= xs.length - 2) return false;
            return branch || segment >= 1;
        }
    }

    /** The jet: whom it burns, when it was fired at them (it shoots out from there) and until when. */
    public record Jet(int targetId, long startTick, long endTick) {
    }

    public static final class State {
        SyncAnomaliesPacket.Entry entry;
        List<Crack> cracks = List.of();
        Vec3 flame = Vec3.ZERO;
        float builtSize = -1;
        int builtCount = -1;
        int nextScan;
        @Nullable
        Jet jet;
        @Nullable
        RazlomLoopSound loop;
        @Nullable
        RazlomJetSound jetSound;
        /** 1 = flame burning, 0 = out (it goes out while the Razlom rests). */
        float flameLevel = 1.0f;
        float prevFlameLevel = 1.0f;
        boolean wasResting;

        public SyncAnomaliesPacket.Entry entry() {
            return entry;
        }

        public List<Crack> cracks() {
            return cracks;
        }

        public Vec3 flame() {
            return flame;
        }

        @Nullable
        public Jet jet() {
            return jet;
        }

        public float flameLevel(float partialTick) {
            return Mth.lerp(partialTick, prevFlameLevel, flameLevel);
        }

        /** How far the jet has shot out along its arc (0..1). */
        public float jetExtend(long now, float partialTick) {
            if (jet == null) return 0.0f;
            return Mth.clamp((now - jet.startTick() + partialTick) / Razlom.JET_GROW_TICKS, 0.0f, 1.0f);
        }

        public boolean jetActive(long now) {
            return jet != null && jet.targetId() >= 0 && now < jet.endTick();
        }
    }

    public static Collection<State> states() {
        return STATES.values();
    }

    @Nullable
    public static State state(BlockPos pos) {
        return STATES.get(pos);
    }

    // ==== packet ======================================================================

    public static void onJet(BlockPos pos, int targetId, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Jet jet = new Jet(targetId, now, now + ticks);
        State state = STATES.get(pos);
        if (state == null) {
            if (targetId >= 0) PENDING_JETS.put(pos.immutable(), jet);
            else PENDING_JETS.remove(pos);
            return;
        }
        state.jet = jet;
    }

    // ==== tick ========================================================================

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

        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Set<BlockPos> seen = new HashSet<>();

        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(level.dimension())) {
            if (!AnomalyTypeIds.RAZLOM.equals(entry.typeId())) continue;
            if (Vec3.atCenterOf(entry.pos()).distanceToSqr(cam) > VISIBLE_RADIUS * VISIBLE_RADIUS) continue;
            seen.add(entry.pos());
            State state = STATES.computeIfAbsent(entry.pos(), p -> {
                // Coming into range while it rests: the flame is simply out, no puff.
                State created = new State();
                created.wasResting = entry.onCooldown();
                created.flameLevel = created.prevFlameLevel = entry.onCooldown() ? 0.0f : 1.0f;
                return created;
            });
            state.entry = entry;
            Jet pending = PENDING_JETS.remove(entry.pos());
            if (pending != null) state.jet = pending;

            int count = crackCount(entry.intensity());
            if (state.builtSize != entry.size() || state.builtCount != count) {
                state.cracks = buildCracks(entry.pos(), entry.size(), count);
                state.builtSize = entry.size();
                state.builtCount = count;
                state.nextScan = 0;
            }
            if (--state.nextScan <= 0) {
                rescan(level, state);
                state.nextScan = RESCAN_TICKS + RANDOM.nextInt(20);
            }

            boolean jetting = state.jetActive(now);
            if (!jetting && state.jet != null && now >= state.jet.endTick()) state.jet = null;

            // The flame goes out while the Razlom rests and flares up again after.
            boolean resting = entry.onCooldown();
            state.prevFlameLevel = state.flameLevel;
            state.flameLevel = resting ? Math.max(0.0f, state.flameLevel - 0.15f) : Math.min(1.0f, state.flameLevel + 0.1f);
            if (resting != state.wasResting) {
                Vec3 f = state.flame;
                for (int i = 0; i < (resting ? 6 : 4); i++) {
                    level.addParticle(resting ? ParticleTypes.SMOKE : ParticleTypes.FLAME,
                            f.x + (RANDOM.nextDouble() - 0.5) * 0.15, f.y, f.z + (RANDOM.nextDouble() - 0.5) * 0.15,
                            (RANDOM.nextDouble() - 0.5) * 0.02, 0.03 + RANDOM.nextDouble() * 0.03, (RANDOM.nextDouble() - 0.5) * 0.02);
                }
                state.wasResting = resting;
            }

            tickSounds(mc, state, jetting);
            tickParticles(level, state, jetting, now);
        }

        for (Iterator<Map.Entry<BlockPos, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, State> e = it.next();
            if (seen.contains(e.getKey())) continue;
            stopSounds(mc, e.getValue());
            it.remove();
        }
        PENDING_JETS.values().removeIf(jet -> jet.endTick() <= now);
    }

    private static void clear(Minecraft mc) {
        for (State state : STATES.values()) stopSounds(mc, state);
        STATES.clear();
        PENDING_JETS.clear();
    }

    private static void stopSounds(Minecraft mc, State state) {
        if (state.loop != null) mc.getSoundManager().stop(state.loop);
        if (state.jetSound != null) mc.getSoundManager().stop(state.jetSound);
        state.loop = null;
        state.jetSound = null;
    }

    public static boolean isTracked(BlockPos pos) {
        return STATES.containsKey(pos);
    }

    /** For the hum: the flame is out while the Razlom rests. */
    public static boolean isResting(BlockPos pos) {
        State state = STATES.get(pos);
        return state != null && state.entry.onCooldown();
    }

    /** For the sounds: is this Razlom's jet burning right now? */
    public static boolean isJetting(BlockPos pos) {
        State state = STATES.get(pos);
        Minecraft mc = Minecraft.getInstance();
        return state != null && mc.level != null && state.jetActive(mc.level.getGameTime());
    }

    private static void tickSounds(Minecraft mc, State state, boolean jetting) {
        if (state.loop == null || state.loop.isStopped()) {
            SoundEvent hum = ForgeRegistries.SOUND_EVENTS.getValue(Razlom.IDLE_SOUND);
            if (hum != null) {
                state.loop = new RazlomLoopSound(state.entry.pos(), hum, state.flame);
                mc.getSoundManager().play(state.loop);
            }
        }
        // The jet's roar; it fades out by itself once the jet is over (see RazlomJetSound). A new
        // jet doesn't wait for the old roar to finish fading.
        if (jetting && (state.jetSound == null || state.jetSound.isStopped() || state.jetSound.isFading())) {
            SoundEvent blow = ForgeRegistries.SOUND_EVENTS.getValue(Razlom.JET_SOUND);
            if (blow != null) {
                state.jetSound = new RazlomJetSound(state.entry.pos(), blow, state.flame);
                mc.getSoundManager().play(state.jetSound);
            }
        }
    }

    private static void tickParticles(ClientLevel level, State state, boolean jetting, long now) {
        int eff = ModClientConfig.effective(state.entry.intensity());
        Vec3 f = state.flame;

        // The hovering flame: small flames licking upwards (none while it's out).
        if (state.flameLevel > 0.5f && RANDOM.nextFloat() < 0.22f) {
            level.addParticle(ParticleTypes.SMALL_FLAME, f.x + (RANDOM.nextDouble() - 0.5) * 0.12, f.y - 0.05,
                    f.z + (RANDOM.nextDouble() - 0.5) * 0.12, 0.0, 0.012 + RANDOM.nextDouble() * 0.01, 0.0);
        }

        // Sparks and wisps rising out of the cracks.
        double crackRate = 0.02 * eff * Math.max(1.0, state.entry.size());
        if (!state.cracks.isEmpty() && RANDOM.nextDouble() < crackRate) {
            Crack crack = state.cracks.get(RANDOM.nextInt(state.cracks.size()));
            int i = RANDOM.nextInt(crack.xs.length);
            if (crack.ys[i] != null) {
                boolean smoke = RANDOM.nextInt(4) == 0;
                level.addParticle(smoke ? ModParticles.HEAT_SMOKE.get() : ModParticles.EMBER.get(),
                        crack.xs[i], crack.ys[i] + 0.03, crack.zs[i], 0.0, 0.008 + RANDOM.nextDouble() * 0.01, 0.0);
            }
        }

        // The jet: flames streaming along its arc (only as far as it has shot out yet).
        if (jetting && state.jet != null) {
            Entity target = level.getEntity(state.jet.targetId());
            if (target != null) {
                Vec3 aim = target.getBoundingBox().getCenter();
                float extend = state.jetExtend(now, 1.0f);
                int n = 3 + eff / 2;
                for (int i = 0; i < n; i++) {
                    double t = extend * RANDOM.nextDouble();
                    Vec3 p = Razlom.jetPoint(f, aim, t);
                    Vec3 ahead = Razlom.jetPoint(f, aim, Math.min(1.0, t + 0.05));
                    Vec3 tangent = ahead.subtract(p);
                    if (tangent.lengthSqr() < 1.0E-8) continue;
                    Vec3 spread = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).scale(0.02);
                    Vec3 v = tangent.normalize().scale(0.1 + RANDOM.nextDouble() * 0.12).add(spread);
                    level.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, v.x, v.y, v.z);
                }
                if (RANDOM.nextInt(3) == 0) {
                    Vec3 p = Razlom.jetPoint(f, aim, extend * RANDOM.nextDouble());
                    level.addParticle(ModParticles.HEAT_SMOKE.get(), p.x, p.y, p.z, 0.0, 0.03, 0.0);
                }
            }
        }
    }

    // ==== cracks ======================================================================

    private static int crackCount(int intensity) {
        return Mth.clamp(2 + ModClientConfig.effective(intensity) / 2, 2, 6);
    }

    /** Lines through the center at spread-out angles, each jagged (a damped random walk sideways,
     *  pinned to zero near the center so they all cross there), wider in the middle, plus a branch
     *  or two. Seeded from the position: the same Razlom always cracks the same way. */
    private static List<Crack> buildCracks(BlockPos pos, double size, int count) {
        RandomSource random = RandomSource.create(pos.asLong() * 0x9E3779B97F4A7C15L + 12345L);
        double radius = Razlom.crackRadius(size);
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        double baseAngle = random.nextDouble() * Math.PI;
        List<Crack> cracks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            double angle = baseAngle + Math.PI * i / count + (random.nextDouble() - 0.5) * 0.5 * Math.PI / count;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double back = radius * (0.6 + random.nextDouble() * 0.4);
            double forward = radius * (0.6 + random.nextDouble() * 0.4);
            Crack main = walk(random, cx, cz, dx, dz, -back, forward, 1.0f, true, false);
            cracks.add(main);

            int branches = random.nextInt(3) == 0 ? 2 : 1;
            for (int b = 0; b < branches; b++) {
                int at = main.xs.length / 2 + (random.nextBoolean() ? 1 : -1) * (int) (main.xs.length * (0.2 + random.nextDouble() * 0.2));
                at = Mth.clamp(at, 0, main.xs.length - 1);
                double turn = (random.nextBoolean() ? 1 : -1) * (0.45 + random.nextDouble() * 0.45);
                double side = at < main.xs.length / 2 ? -1 : 1;
                double bx = Math.cos(angle + turn) * side;
                double bz = Math.sin(angle + turn) * side;
                double length = radius * (0.25 + random.nextDouble() * 0.3);
                cracks.add(walk(random, main.xs[at], main.zs[at], bx, bz, 0, length, 0.55f, false, true));
            }
        }
        return cracks;
    }

    private static Crack walk(RandomSource random, double ox, double oz, double dx, double dz,
                              double from, double to, float width, boolean pinCenter, boolean branch) {
        int n = Math.max(2, (int) Math.ceil((to - from) / STEP) + 1);
        double[] xs = new double[n];
        double[] zs = new double[n];
        float[] widths = new float[n];
        double px = -dz;
        double pz = dx;
        double offset = 0;
        for (int i = 0; i < n; i++) {
            double t = from + (to - from) * i / (n - 1);
            offset = offset * 0.85 + random.nextGaussian() * 0.07;
            offset = Mth.clamp(offset, -0.25, 0.25);
            double pin = pinCenter ? Mth.clamp(Math.abs(t) / 0.5, 0.0, 1.0) : Mth.clamp(i / 3.0, 0.0, 1.0);
            xs[i] = ox + dx * t + px * offset * pin;
            zs[i] = oz + dz * t + pz * offset * pin;
            double along = pinCenter ? Math.abs(t) / Math.max(Math.abs(from), Math.abs(to)) : i / (double) (n - 1);
            widths[i] = width * (float) (1.0 - 0.7 * along);
        }
        return new Crack(xs, zs, widths, branch);
    }

    /** Re-reads the ground under every crack point and the flame's position. */
    private static void rescan(ClientLevel level, State state) {
        SyncAnomaliesPacket.Entry entry = state.entry;
        state.flame = Razlom.flamePos(level, entry.pos(), entry.size());
        AABB zone = AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
        double fromY = Math.max(state.flame.y, zone.minY);
        double toY = zone.minY - Razlom.GROUND_SEARCH_BELOW;
        for (Crack crack : state.cracks) {
            for (int i = 0; i < crack.xs.length; i++) {
                crack.ys[i] = Razlom.groundY(level, crack.xs[i], crack.zs[i], fromY, toY);
            }
        }
    }
}
