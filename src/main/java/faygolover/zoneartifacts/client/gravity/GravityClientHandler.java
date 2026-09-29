package faygolover.zoneartifacts.client.gravity;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.anomaly.Gravity;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.client.ClientAnomalyCache;
import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.GravityEventPacket;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

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
 * Client side of the gravitational anomalies:
 * <ul>
 *     <li><b>your own player's movement</b> (the client owns it, so the pull, the whirl and the
 *     cushion are applied here, with the same physics the server uses for mobs — {@link Gravity});
 *     throws, damage and shock waves still come from the server;</li>
 *     <li>the <b>debris</b> — small spinning bits of the surrounding blocks (dirt, gravel, leaves,
 *     pebbles) that lie, circle, get sucked in, orbit, fly off or hang, per anomaly;</li>
 *     <li>dust and haze particles.</li>
 * </ul>
 * Drawn by {@link GravityRenderer}.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GravityClientHandler {

    public static final double VISIBLE_RADIUS = 48.0;
    /** How long an anomaly takes to show itself again after its cooldown, ticks. */
    public static final float FADE_IN_TICKS = 50.0f;
    private static final RandomSource RANDOM = RandomSource.create();

    public enum Kind { PLESH, VORONKA, KARUSEL, PODUSHKA }

    /** A small spinning bit of a block. */
    public static final class Debris {
        BlockState state;
        Vec3 pos;
        Vec3 prevPos;
        Vec3 velocity = Vec3.ZERO;
        float size;
        Vector3f axis;
        float angle;
        float prevAngle;
        float spin;
        double orbitRadius;
        double orbitAngle;
        double orbitHeight;
        double orbitSpeed;
        double bob;
        boolean free;
        int life;
        /** 0..1: a new bit grows in instead of popping up. */
        float grow;
        float growRate = 1.0f / 30.0f;
        /** Voronka: how far into the pull this bit came in from the edge. */
        float startProgress;

        public BlockState state() {
            return state;
        }

        public Vec3 pos(float partial) {
            return prevPos.lerp(pos, partial);
        }

        public float angle(float partial) {
            return Mth.lerp(partial, prevAngle, angle);
        }

        public float size() {
            float g = grow * grow * (3.0f - 2.0f * grow);
            return size * g;
        }

        public Vector3f axis() {
            return axis;
        }
    }

    /** A brief twinkle inside a Karusel. */
    public record Glint(Vec3 pos, long born, int life, float size) {
    }

    public static final class State {
        SyncAnomaliesPacket.Entry entry;
        Kind kind;
        long activeStart = -1;
        int seed;
        long releaseTick = -1_000_000L;
        @Nullable
        Double groundY;
        int nextScan;
        List<BlockState> palette = List.of();
        final List<Debris> debris = new ArrayList<>();
        final List<Glint> glints = new ArrayList<>();
        @Nullable
        Gravity.Bounce localBounce;
        /** On cooldown right now (as last seen). */
        boolean resting;
        /** Game time the cooldown last ended; long ago = fully shown. */
        long readySince = -1_000_000L;
        @Nullable
        GravityLoopSound idleLoop;
        /** When the next idle rustle is due (Plesh, Karusel). */
        long nextRustle = -1;

        public SyncAnomaliesPacket.Entry entry() {
            return entry;
        }

        public Kind kind() {
            return kind;
        }

        @Nullable
        public Double groundY() {
            return groundY;
        }

        public List<Debris> debris() {
            return debris;
        }

        public List<Glint> glints() {
            return glints;
        }

        public long releaseTick() {
            return releaseTick;
        }

        /** Pull / spin phase running. */
        public boolean active() {
            return entry.active() && activeStart >= 0;
        }

        /** 0 on cooldown, then fading in to 1 over {@link #FADE_IN_TICKS} once it's ready again. */
        public float readiness(long now, float partial) {
            if (resting) return 0.0f;
            return Mth.clamp((now - readySince + partial) / FADE_IN_TICKS, 0.0f, 1.0f);
        }

        /** 0..1 through the current phase. */
        public float progress(long now, float partial) {
            if (!active()) return 0.0f;
            return Mth.clamp((now - activeStart + partial) / (float) phaseTicks(kind), 0.0f, 1.0f);
        }

        public Vec3 center() {
            return Gravity.center(entry.pos(), entry.size());
        }

        public AABB zone() {
            return AnomalyGeometry.centeredAabb(entry.pos(), entry.size());
        }
    }

    private static final Map<BlockPos, State> STATES = new HashMap<>();

    private GravityClientHandler() {
    }

    public static Collection<State> states() {
        return STATES.values();
    }

    /** Still shown (not removed, not out of range). */
    static boolean isTracked(State state) {
        return state.entry != null && STATES.get(state.entry.pos()) == state;
    }

    /** Phase length for the look of it (the server decides when it really ends). The common
     *  config is loaded on the client too — from its own file, normally the same values. */
    private static int phaseTicks(Kind kind) {
        try {
            return switch (kind) {
                case KARUSEL -> Gravity.phaseTicks(AnomalyTypeIds.KARUSEL, ModCommonConfig.KARUSEL_SPIN_SECONDS.get());
                case VORONKA -> Gravity.phaseTicks(AnomalyTypeIds.VORONKA, ModCommonConfig.VORONKA_PULL_SECONDS.get());
                default -> Gravity.phaseTicks(AnomalyTypeIds.PLESH, ModCommonConfig.PLESH_PULL_SECONDS.get());
            };
        } catch (IllegalStateException notLoaded) {
            return switch (kind) {
                case KARUSEL -> 120;
                case VORONKA -> 60;
                default -> 50;
            };
        }
    }

    private static double podushkaHeight() {
        try {
            return ModCommonConfig.PODUSHKA_HEIGHT.get();
        } catch (IllegalStateException notLoaded) {
            return 3.0;
        }
    }

    @Nullable
    private static Kind kindOf(SyncAnomaliesPacket.Entry entry) {
        if (AnomalyTypeIds.PLESH.equals(entry.typeId())) return Kind.PLESH;
        if (AnomalyTypeIds.VORONKA.equals(entry.typeId())) return Kind.VORONKA;
        if (AnomalyTypeIds.KARUSEL.equals(entry.typeId())) return Kind.KARUSEL;
        if (AnomalyTypeIds.PODUSHKA.equals(entry.typeId())) return Kind.PODUSHKA;
        return null;
    }

    // ==== events from the server ============================================================

    public static void onEvent(BlockPos pos, byte event, int seed, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        State state = STATES.get(pos);
        long now = level.getGameTime();
        switch (event) {
            case GravityEventPacket.START -> {
                if (state != null) {
                    state.activeStart = now;
                    state.seed = seed;
                }
            }
            case GravityEventPacket.RELEASE -> {
                if (state != null) {
                    state.activeStart = -1;
                    state.releaseTick = now;
                    onRelease(level, state);
                }
            }
            case GravityEventPacket.BOUNCE -> {
                for (int i = 0; i < 8; i++) {
                    level.addParticle(ModParticles.FROST_MIST.get(), at.x + (RANDOM.nextDouble() - 0.5) * 0.6, at.y + 0.1,
                            at.z + (RANDOM.nextDouble() - 0.5) * 0.6, (RANDOM.nextDouble() - 0.5) * 0.04, 0.02, (RANDOM.nextDouble() - 0.5) * 0.04);
                }
            }
            default -> {
            }
        }
    }

    private static void onRelease(ClientLevel level, State state) {
        Vec3 c = state.center();
        switch (state.kind) {
            case PLESH -> {
                for (Debris d : state.debris) {
                    d.free = true;
                    d.life = 40 + RANDOM.nextInt(20);
                    d.velocity = Gravity.throwDirection(RANDOM).scale(0.4 + RANDOM.nextDouble() * 0.4);
                    d.spin *= 4.0f;
                }
                dustBurst(level, c, 40, 0.2, 0.5);
            }
            case VORONKA -> {
                for (Debris d : state.debris) {
                    level.addParticle(ParticleTypes.POOF, d.pos.x, d.pos.y, d.pos.z, 0.0, 0.02, 0.0);
                }
                state.debris.clear();
                dustBurst(level, c, 55, 0.25, 0.6);
            }
            case KARUSEL -> {
                // The wave of compressed air goes out flat: dust and bits fly off sideways.
                double ground = state.groundY != null ? state.groundY : state.zone().minY;
                double reach = Gravity.reach(state.entry.size());
                for (int i = 0; i < 90; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2.0;
                    double speed = 0.35 + RANDOM.nextDouble() * 0.35;
                    double r = 0.2 + RANDOM.nextDouble() * 0.6;
                    double y = ground + 0.1 + Math.pow(RANDOM.nextDouble(), 2.0) * reach * 0.8;
                    level.addParticle(ModParticles.GRAV_DUST.get(), c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r,
                            Math.cos(a) * speed, 0.005, Math.sin(a) * speed);
                }
                for (Debris d : state.debris) {
                    Vec3 out = new Vec3(d.pos.x - c.x, 0.0, d.pos.z - c.z);
                    out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
                    d.free = true;
                    d.life = 25 + RANDOM.nextInt(15);
                    d.velocity = out.scale(0.45 + RANDOM.nextDouble() * 0.3).add(0.0, 0.08, 0.0);
                    d.spin *= 3.0f;
                }
            }
            default -> {
            }
        }
    }

    private static void dustBurst(ClientLevel level, Vec3 c, int count, double minSpeed, double maxSpeed) {
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), Math.abs(RANDOM.nextGaussian()) * 0.6, RANDOM.nextGaussian()).normalize();
            Vec3 v = dir.scale(minSpeed + RANDOM.nextDouble() * (maxSpeed - minSpeed));
            level.addParticle(ModParticles.GRAV_DUST.get(), c.x, c.y, c.z, v.x, v.y, v.z);
        }
    }

    // ==== your own player ======================================================================

    @SubscribeEvent
    public static void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;
        if (player.isCreative() || player.isSpectator() || player.isPassenger() || player.getAbilities().flying) return;
        long now = mc.level.getGameTime();

        for (State state : STATES.values()) {
            SyncAnomaliesPacket.Entry entry = state.entry;
            double force = entry.speed();
            Vec3 v = player.getDeltaMovement();
            switch (state.kind) {
                case PLESH, VORONKA -> {
                    if (!state.active() || !Gravity.inSphere(player, entry.pos(), entry.size())) continue;
                    Gravity.Orbit orbit = state.kind == Kind.PLESH ? Gravity.Orbit.of(state.seed, player.getId()) : Gravity.Orbit.tight(state.seed, player.getId());
                    player.setDeltaMovement(Gravity.pull(player.getBoundingBox().getCenter(), v, state.center(), force,
                            Gravity.gravityOf(player), orbit, now - state.activeStart));
                }
                case KARUSEL -> {
                    if (!state.active() || !Gravity.inCylinder(player, entry.pos(), entry.size())) continue;
                    player.setDeltaMovement(Gravity.swirl(player.position(), v, state.center(), Gravity.reach(entry.size()), force, player.onGround()));
                }
                case PODUSHKA -> {
                    AABB zone = state.zone();
                    if (!player.getBoundingBox().intersects(zone)) {
                        state.localBounce = null;
                        continue;
                    }
                    player.resetFallDistance();
                    if (player.isShiftKeyDown()) {
                        state.localBounce = null;
                        player.setDeltaMovement(Gravity.sink(v, Gravity.gravityOf(player)));
                        continue;
                    }
                    if (state.localBounce == null) state.localBounce = Gravity.startBounce(v, RANDOM);
                    double height = podushkaHeight() * force;
                    player.setDeltaMovement(Gravity.bounce(v, player.getY(), zone.maxY, height, Gravity.gravityOf(player), state.localBounce));
                }
            }
        }
    }

    // ==== states, debris, particles =================================================================

    @SubscribeEvent
    public static void onClientTickEnd(TickEvent.ClientTickEvent event) {
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
            Kind kind = kindOf(entry);
            if (kind == null) continue;
            if (Vec3.atCenterOf(entry.pos()).distanceToSqr(cam) > VISIBLE_RADIUS * VISIBLE_RADIUS) continue;
            seen.add(entry.pos());
            State state = STATES.computeIfAbsent(entry.pos(), p -> new State());
            boolean fresh = state.entry == null;
            state.entry = entry;
            state.kind = kind;
            if (entry.onCooldown()) {
                state.resting = true;
            } else if (state.resting || fresh) {
                // Came off cooldown now: fade in. (Seen for the first time already ready: shown at once.)
                state.readySince = fresh ? now - 1_000_000L : now;
                state.resting = false;
            }
            if (entry.active() && state.activeStart < 0) state.activeStart = now; // joined mid-phase
            if (!entry.active()) state.activeStart = -1;

            if (--state.nextScan <= 0) {
                scan(level, state);
                state.nextScan = 80 + RANDOM.nextInt(20);
            }
            tickDebris(level, state, now);
            tickParticles(level, state, now);
            state.glints.removeIf(g -> now > g.born() + g.life());
            tickIdleSound(mc, state, now);
        }
        STATES.keySet().removeIf(pos -> !seen.contains(pos));
    }

    /**
     * Idle sounds: Voronka hums (a loop), Plesh and Karusel now and then rustle with wind and dust
     * somewhere inside. Nothing on cooldown; after it they come back with the anomaly.
     */
    private static void tickIdleSound(Minecraft mc, State state, long now) {
        if (state.kind == Kind.VORONKA) {
            if (state.idleLoop == null || state.idleLoop.isStopped()) {
                state.idleLoop = new GravityLoopSound(state, ModSounds.VORONKA_IDLE.get(), state.center(), 0.45f);
                mc.getSoundManager().play(state.idleLoop);
            }
            return;
        }
        if (state.kind != Kind.PLESH && state.kind != Kind.KARUSEL) return;
        if (state.nextRustle < 0) state.nextRustle = now + 20 + RANDOM.nextInt(100);
        if (now < state.nextRustle) return;
        boolean karusel = state.kind == Kind.KARUSEL;
        state.nextRustle = now + (karusel ? 60 + RANDOM.nextInt(80) : 80 + RANDOM.nextInt(100));
        float ready = state.readiness(now, 0.0f);
        if (state.active() || ready < 0.5f) return;
        AABB zone = state.zone();
        double ground = state.groundY != null ? state.groundY : zone.minY;
        double x = Mth.lerp(RANDOM.nextDouble(), zone.minX, zone.maxX);
        double z = Mth.lerp(RANDOM.nextDouble(), zone.minZ, zone.maxZ);
        mc.getSoundManager().play(new SimpleSoundInstance(karusel ? ModSounds.KARUSEL_IDLE.get() : ModSounds.PLESH_IDLE.get(),
                SoundSource.AMBIENT, (karusel ? 0.6f : 0.5f) * ready, 0.88f + RANDOM.nextFloat() * 0.24f,
                RandomSource.create(), x, ground + 0.3, z));
    }

    /** Ground under the zone and the blocks the debris is made of. */
    private static void scan(ClientLevel level, State state) {
        AABB zone = state.zone();
        Vec3 c = zone.getCenter();
        state.groundY = Razlom.groundY(level, c.x, c.z, zone.maxY, zone.minY - 3);
        List<BlockState> palette = new ArrayList<>();
        int y = state.groundY != null ? Mth.floor(state.groundY) - 1 : Mth.floor(zone.minY) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    pos.set(Mth.floor(c.x) + dx, y + dy, Mth.floor(c.z) + dz);
                    BlockState s = level.getBlockState(pos);
                    if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                    if (s.isSolidRender(level, pos) || s.is(BlockTags.LEAVES)) palette.add(s);
                }
            }
        }
        switch (state.kind) {
            case KARUSEL -> {
                palette.add(Blocks.OAK_LEAVES.defaultBlockState());
                palette.add(Blocks.BIRCH_LEAVES.defaultBlockState());
                palette.add(Blocks.DIRT.defaultBlockState());
            }
            case PODUSHKA -> {
                palette.add(Blocks.GRAVEL.defaultBlockState());
                palette.add(Blocks.COBBLESTONE.defaultBlockState());
                palette.add(Blocks.ANDESITE.defaultBlockState());
            }
            default -> {
                palette.add(Blocks.GRAVEL.defaultBlockState());
                palette.add(Blocks.DIRT.defaultBlockState());
                palette.add(Blocks.COARSE_DIRT.defaultBlockState());
            }
        }
        state.palette = palette;
    }

    private static int debrisCount(State state) {
        int eff = ModClientConfig.effective(state.entry.intensity());
        double sizeFactor = Mth.clamp(state.entry.size(), 1.0, 4.0);
        return switch (state.kind) {
            case PLESH -> Mth.clamp((int) ((3 + eff) * sizeFactor), 3, 40);
            case VORONKA -> Mth.clamp((int) ((3 + eff) * sizeFactor), 3, 32);
            case KARUSEL -> Mth.clamp((int) ((4 + eff) * sizeFactor), 4, 48);
            case PODUSHKA -> Mth.clamp((int) ((3 + eff) * sizeFactor), 3, 40);
        };
    }

    private static Debris newDebris(State state, float progress) {
        Debris d = new Debris();
        d.state = state.palette.isEmpty() ? Blocks.GRAVEL.defaultBlockState() : state.palette.get(RANDOM.nextInt(state.palette.size()));
        d.size = (float) (0.06 + RANDOM.nextDouble() * 0.08);
        d.axis = new Vector3f((float) RANDOM.nextGaussian(), (float) RANDOM.nextGaussian(), (float) RANDOM.nextGaussian()).normalize();
        d.angle = RANDOM.nextFloat() * 6.28f;
        d.prevAngle = d.angle;
        d.spin = 0.02f + RANDOM.nextFloat() * 0.06f;
        double half = state.entry.size() * 0.5;
        double reach = Gravity.reach(state.entry.size());
        d.orbitAngle = RANDOM.nextDouble() * Math.PI * 2.0;
        d.bob = RANDOM.nextDouble() * Math.PI * 2.0;
        switch (state.kind) {
            case PLESH -> {
                d.orbitRadius = half * (0.3 + RANDOM.nextDouble() * 0.6);
                d.orbitHeight = 0.05 + RANDOM.nextDouble() * 0.2;
                d.orbitSpeed = 0.008 + RANDOM.nextDouble() * 0.008;
            }
            case VORONKA -> {
                // Comes in from the edge of the pull sphere, from any direction.
                d.orbitRadius = reach * (0.8 + RANDOM.nextDouble() * 0.2);
                d.orbitHeight = (RANDOM.nextDouble() * 2.0 - 1.0) * 0.8;
                d.orbitSpeed = 0.01 + RANDOM.nextDouble() * 0.01;
                d.startProgress = progress;
                d.growRate = 1.0f / 8.0f;
            }
            case KARUSEL -> {
                d.orbitRadius = reach * (0.25 + RANDOM.nextDouble() * 0.7);
                d.orbitHeight = 0.05 + RANDOM.nextDouble() * 0.25;
                d.orbitSpeed = 0.015 + RANDOM.nextDouble() * 0.015;
                d.size *= 0.8f;
            }
            case PODUSHKA -> {
                // Spread evenly through the whole cushion, not bunched at its middle.
                d.orbitRadius = half * 0.92 * Math.sqrt(RANDOM.nextDouble());
                d.orbitHeight = (RANDOM.nextDouble() - 0.5) * state.entry.size() * 0.85;
                d.orbitSpeed = 0.002 + RANDOM.nextDouble() * 0.003;
                d.spin = 0.005f + RANDOM.nextFloat() * 0.015f;
            }
        }
        d.pos = target(state, d, progress, 0L);
        d.prevPos = d.pos;
        return d;
    }

    /** Where a debris bit wants to be right now. */
    private static Vec3 target(State state, Debris d, float progress, long now) {
        Vec3 c = state.center();
        double ground = state.groundY != null ? state.groundY : state.zone().minY;
        double bob = Math.sin(now * 0.08 + d.bob);
        return switch (state.kind) {
            case PLESH -> {
                double r = d.orbitRadius * (1.0 - 0.85 * progress);
                double y = Mth.lerp(progress, ground + d.orbitHeight + 0.03 * bob, c.y + d.orbitHeight - 0.15);
                yield new Vec3(c.x + Math.cos(d.orbitAngle) * r, y, c.z + Math.sin(d.orbitAngle) * r);
            }
            case VORONKA -> {
                // From the edge down to the center by the end of the pull.
                double local = Mth.clamp((progress - d.startProgress) / Math.max(0.05, 1.0 - d.startProgress), 0.0, 1.0);
                double r = d.orbitRadius * Math.pow(1.0 - local, 1.4) + 0.04;
                double flat = Math.sqrt(1.0 - d.orbitHeight * d.orbitHeight);
                yield new Vec3(c.x + Math.cos(d.orbitAngle) * r * flat, c.y + d.orbitHeight * r, c.z + Math.sin(d.orbitAngle) * r * flat);
            }
            case KARUSEL -> {
                double reach = Gravity.reach(state.entry.size());
                double lift = progress > 0 ? reach * 0.45 * (0.5 + 0.5 * Math.sin(d.bob + now * 0.05)) : 0.0;
                yield new Vec3(c.x + Math.cos(d.orbitAngle) * d.orbitRadius, ground + d.orbitHeight + lift + 0.04 * bob,
                        c.z + Math.sin(d.orbitAngle) * d.orbitRadius);
            }
            case PODUSHKA -> new Vec3(c.x + Math.cos(d.orbitAngle) * d.orbitRadius, c.y + d.orbitHeight + 0.08 * bob,
                    c.z + Math.sin(d.orbitAngle) * d.orbitRadius);
        };
    }

    private static void tickDebris(ClientLevel level, State state, long now) {
        float progress = state.progress(now, 0.0f);
        boolean resting = state.entry.onCooldown();
        List<Debris> list = state.debris;

        // Top up (not while things are still flying off or the anomaly rests after a release).
        boolean anyFree = false;
        for (Debris d : list) anyFree |= d.free;
        int want = debrisCount(state);
        switch (state.kind) {
            case PLESH -> {
                // After the cooldown the dust comes back bit by bit, each one growing in.
                if (!anyFree && !resting && list.size() < want && now % 3 == 0) list.add(newDebris(state, 0.0f));
            }
            case VORONKA -> {
                // Nothing at rest; while pulling, bits come in from the edge through the first 60 %.
                if (!state.active()) {
                    if (!anyFree) list.clear();
                } else {
                    int due = (int) Math.ceil(want * Math.min(1.0f, progress / 0.6f));
                    if (list.size() < due) list.add(newDebris(state, progress));
                }
            }
            default -> {
                if (!anyFree) {
                    while (list.size() < want) list.add(newDebris(state, 0.0f));
                }
            }
        }
        while (list.size() > want && !anyFree) list.remove(list.size() - 1);

        double ground = state.groundY != null ? state.groundY : state.zone().minY;
        for (Iterator<Debris> it = list.iterator(); it.hasNext(); ) {
            Debris d = it.next();
            d.prevPos = d.pos;
            d.prevAngle = d.angle;
            d.angle += d.spin;
            d.grow = Math.min(1.0f, d.grow + d.growRate);
            if (d.free) {
                d.velocity = d.velocity.scale(0.98).add(0.0, -0.04, 0.0);
                d.pos = d.pos.add(d.velocity);
                if (--d.life <= 0 || d.pos.y < ground - 0.5) it.remove();
                continue;
            }
            double speedUp = switch (state.kind) {
                case PLESH -> 1.0 + 20.0 * progress;
                case VORONKA -> 1.0 + 15.0 * progress;
                case KARUSEL -> state.active() ? 6.0 : 1.0;
                case PODUSHKA -> 1.0;
            };
            d.orbitAngle += d.orbitSpeed * speedUp;
            Vec3 target = target(state, d, progress, now);
            d.pos = d.pos.add(target.subtract(d.pos).scale(state.active() ? 0.2 : 0.1));
        }
    }

    private static void tickParticles(ClientLevel level, State state, long now) {
        int eff = ModClientConfig.effective(state.entry.intensity());
        Vec3 c = state.center();
        double reach = Gravity.reach(state.entry.size());
        double half = state.entry.size() * 0.5;
        float progress = state.progress(now, 0.0f);
        boolean active = state.active();
        switch (state.kind) {
            case PLESH, VORONKA -> {
                if (active) {
                    // Dust drawn in from all around the pull sphere.
                    int n = roll(0.35 * eff * (0.5 + progress));
                    for (int i = 0; i < n; i++) {
                        Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                        Vec3 p = c.add(dir.scale(reach * (0.7 + RANDOM.nextDouble() * 0.3)));
                        Vec3 v = c.subtract(p).scale(1.0 / 24.0);
                        level.addParticle(ModParticles.GRAV_DUST.get(), p.x, p.y, p.z, v.x, v.y, v.z);
                    }
                } else if (state.kind == Kind.PLESH && RANDOM.nextDouble() < 0.04 * eff * state.readiness(now, 0.0f)) {
                    // A speck drifting round lazily (Voronka shows nothing at rest).
                    double a = RANDOM.nextDouble() * Math.PI * 2.0;
                    double r = half * RANDOM.nextDouble();
                    double y = state.groundY != null ? state.groundY + 0.1 : c.y;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r);
                    level.addParticle(ModParticles.GRAV_DUST.get(), p.x, p.y, p.z, -Math.sin(a) * 0.02, 0.005, Math.cos(a) * 0.02);
                }
            }
            case KARUSEL -> {
                double ground = state.groundY != null ? state.groundY : state.zone().minY;
                float ready = state.readiness(now, 0.0f);
                if (active) {
                    // The funnel: dust drawn up the axis in a tight helix.
                    int m = roll(0.35 * eff);
                    for (int i = 0; i < m; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2.0;
                        double r = 0.15 + RANDOM.nextDouble() * 0.35;
                        Vec3 p = new Vec3(c.x + Math.cos(a) * r, ground + 0.1 + RANDOM.nextDouble() * 0.4, c.z + Math.sin(a) * r);
                        level.addParticle(ModParticles.GRAV_DUST.get(), p.x, p.y, p.z,
                                -Math.sin(a) * 0.18, 0.09 + RANDOM.nextDouble() * 0.06, Math.cos(a) * 0.18);
                    }
                }
                int n = roll((active ? 0.6 : 0.15 * ready) * eff);
                for (int i = 0; i < n; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2.0;
                    double r = reach * Math.sqrt(RANDOM.nextDouble());
                    double h = 0.05 + RANDOM.nextDouble() * (active ? reach * 0.6 : 0.4);
                    double tangential = active ? 0.25 : 0.05;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * r, ground + h, c.z + Math.sin(a) * r);
                    level.addParticle(ModParticles.GRAV_DUST.get(), p.x, p.y, p.z,
                            -Math.sin(a) * tangential - Math.cos(a) * 0.02, active ? 0.02 : 0.0, Math.cos(a) * tangential - Math.sin(a) * 0.02);
                }
                // Twinkles: a slight shimmer, more of it while spinning.
                if (RANDOM.nextDouble() < (active ? 0.3 : 0.05 * ready) * eff) {
                    double a = RANDOM.nextDouble() * Math.PI * 2.0;
                    double r = reach * Math.sqrt(RANDOM.nextDouble());
                    Vec3 p = new Vec3(c.x + Math.cos(a) * r, ground + RANDOM.nextDouble() * reach, c.z + Math.sin(a) * r);
                    state.glints.add(new Glint(p, now, 6 + RANDOM.nextInt(7), 0.04f + RANDOM.nextFloat() * 0.05f));
                }
            }
            case PODUSHKA -> {
                // Bluish haze in wisps.
                if (RANDOM.nextDouble() < 0.08 * eff * Math.max(1.0, state.entry.size())) {
                    AABB zone = state.zone();
                    Vec3 p = new Vec3(Mth.lerp(RANDOM.nextDouble(), zone.minX, zone.maxX), Mth.lerp(RANDOM.nextDouble(), zone.minY, zone.maxY),
                            Mth.lerp(RANDOM.nextDouble(), zone.minZ, zone.maxZ));
                    level.addParticle(ModParticles.FROST_MIST.get(), p.x, p.y, p.z,
                            (RANDOM.nextDouble() - 0.5) * 0.01, 0.003, (RANDOM.nextDouble() - 0.5) * 0.01);
                }
            }
        }
    }

    private static int roll(double rate) {
        int n = (int) rate;
        if (RANDOM.nextDouble() < rate - n) n++;
        return n;
    }
}
