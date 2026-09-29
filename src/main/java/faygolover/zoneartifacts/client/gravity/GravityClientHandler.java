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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
            return size;
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

    /** Phase length for the look of it (the server decides when it really ends). The common
     *  config is loaded on the client too — from its own file, normally the same values. */
    private static int phaseTicks(Kind kind) {
        try {
            double seconds = switch (kind) {
                case KARUSEL -> ModCommonConfig.KARUSEL_SPIN_SECONDS.get();
                case VORONKA -> ModCommonConfig.VORONKA_PULL_SECONDS.get();
                default -> ModCommonConfig.PLESH_PULL_SECONDS.get();
            };
            return Math.max(1, (int) Math.round(seconds * 20.0));
        } catch (IllegalStateException notLoaded) {
            return kind == Kind.KARUSEL ? 100 : 60;
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
                double y = state.zone().minY + 0.3;
                dustBurst(level, new Vec3(c.x, y, c.z), 30, 0.1, 0.35);
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
                    Gravity.Orbit orbit = state.kind == Kind.PLESH ? Gravity.Orbit.of(state.seed, player.getId()) : Gravity.Orbit.STILL;
                    player.setDeltaMovement(Gravity.pull(player.getBoundingBox().getCenter(), v, state.center(), force,
                            Gravity.gravityOf(player), orbit, now - state.activeStart));
                }
                case KARUSEL -> {
                    if (!state.active() || !Gravity.inCylinder(player, entry.pos(), entry.size())) continue;
                    player.setDeltaMovement(Gravity.swirl(player.position(), v, state.center(), force, player.onGround()));
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
            state.entry = entry;
            state.kind = kind;
            if (entry.active() && state.activeStart < 0) state.activeStart = now; // joined mid-phase
            if (!entry.active()) state.activeStart = -1;

            if (--state.nextScan <= 0) {
                scan(level, state);
                state.nextScan = 80 + RANDOM.nextInt(20);
            }
            tickDebris(level, state, now);
            tickParticles(level, state, now);
            state.glints.removeIf(g -> now > g.born() + g.life());
        }
        STATES.keySet().removeIf(pos -> !seen.contains(pos));
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
            case VORONKA -> Mth.clamp((int) ((2 + eff / 2) * sizeFactor), 2, 24);
            case KARUSEL -> Mth.clamp((int) ((4 + eff) * sizeFactor), 4, 48);
            case PODUSHKA -> Mth.clamp((int) ((3 + eff) * sizeFactor), 3, 40);
        };
    }

    private static Debris newDebris(State state) {
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
                d.orbitRadius = half * (0.25 + RANDOM.nextDouble() * 0.35);
                d.orbitHeight = (RANDOM.nextDouble() - 0.5) * half * 0.4;
                d.orbitSpeed = 0.01 + RANDOM.nextDouble() * 0.01;
            }
            case KARUSEL -> {
                d.orbitRadius = reach * (0.25 + RANDOM.nextDouble() * 0.7);
                d.orbitHeight = 0.05 + RANDOM.nextDouble() * 0.25;
                d.orbitSpeed = 0.015 + RANDOM.nextDouble() * 0.015;
                d.size *= 0.8f;
            }
            case PODUSHKA -> {
                d.orbitRadius = half * RANDOM.nextDouble() * 0.85;
                d.orbitHeight = (RANDOM.nextDouble() - 0.5) * state.entry.size() * 0.8;
                d.orbitSpeed = 0.002 + RANDOM.nextDouble() * 0.003;
                d.spin = 0.005f + RANDOM.nextFloat() * 0.015f;
            }
        }
        d.pos = target(state, d, 0.0f, 0L);
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
                double r = d.orbitRadius * (1.0 - 0.9 * progress);
                yield new Vec3(c.x + Math.cos(d.orbitAngle) * r, c.y + d.orbitHeight * (1.0 - progress) + 0.05 * bob, c.z + Math.sin(d.orbitAngle) * r);
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
        if (!anyFree && !(resting && state.kind != Kind.KARUSEL && state.kind != Kind.PODUSHKA)) {
            while (list.size() < want) list.add(newDebris(state));
        }
        while (list.size() > want && !anyFree) list.remove(list.size() - 1);

        double ground = state.groundY != null ? state.groundY : state.zone().minY;
        for (Iterator<Debris> it = list.iterator(); it.hasNext(); ) {
            Debris d = it.next();
            d.prevPos = d.pos;
            d.prevAngle = d.angle;
            d.angle += d.spin;
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
                } else if (RANDOM.nextDouble() < 0.04 * eff) {
                    // A speck drifting round lazily.
                    double a = RANDOM.nextDouble() * Math.PI * 2.0;
                    double r = half * RANDOM.nextDouble();
                    double y = state.kind == Kind.PLESH && state.groundY != null ? state.groundY + 0.1 : c.y;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r);
                    level.addParticle(ModParticles.GRAV_DUST.get(), p.x, p.y, p.z, -Math.sin(a) * 0.02, 0.005, Math.cos(a) * 0.02);
                }
            }
            case KARUSEL -> {
                double ground = state.groundY != null ? state.groundY : state.zone().minY;
                int n = roll((active ? 0.6 : 0.15) * eff);
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
                if (RANDOM.nextDouble() < (active ? 0.3 : 0.05) * eff) {
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
