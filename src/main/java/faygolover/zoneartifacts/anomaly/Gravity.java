package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared physics of the gravitational anomalies — used by the server for mobs, items and
 * projectiles, and by each client for its own player (whose movement the client owns), so both
 * move the same way.
 * <ul>
 *     <li><b>Plesh</b>: pull into a sphere of radius {@link #reach} (twice the zone's half-size);
 *     what's caught circles on a small orbit; then a throw up and away.</li>
 *     <li><b>Voronka</b>: the same pull without orbits, then a tear: damage, destroyed loot, gore.</li>
 *     <li><b>Karusel</b>: sideways pull and spin in a cylinder (radius {@link #reach}, as high), no
 *     vertical pull; on the ground a sprint gets you out, in the air there's no grip.</li>
 *     <li><b>Podushka</b>: brake a fall, then bounce above its top; sneaking sinks gently.</li>
 * </ul>
 */
public final class Gravity {

    /** Something caught within this distance of the center stops being pulled and orbits (Plesh)
     *  or is held (Voronka). */
    public static final double CAPTURE_RADIUS = 1.0;

    // ---- sounds (vanilla-backed placeholders in sounds.json) ------------------------------
    public static final ResourceLocation PLESH_PULL_SOUND = id("plesh_pull");
    public static final ResourceLocation PLESH_THROW_SOUND = id("plesh_throw");
    public static final ResourceLocation VORONKA_PULL_SOUND = id("voronka_pull");
    public static final ResourceLocation VORONKA_BURST_SOUND = id("voronka_burst");
    public static final ResourceLocation KARUSEL_SPIN_SOUND = id("karusel_spin");
    public static final ResourceLocation KARUSEL_HIT_SOUND = id("karusel_hit");
    public static final ResourceLocation PODUSHKA_BOUNCE_SOUND = id("podushka_bounce");
    public static final ResourceLocation GORE_SOUND = id("gore_splat");

    public static final ResourceLocation GRAVITY_DAMAGE_TYPE = id("anomaly_gravity");
    public static final ResourceLocation IMPACT_DAMAGE_TYPE = id("anomaly_impact");

    private Gravity() {
    }

    // ==== geometry ======================================================================

    /** Reach of the pull (sphere radius / Karusel cylinder radius and height): twice the zone's half-size. */
    public static double reach(double size) {
        return size;
    }

    public static Vec3 center(BlockPos pos, double size) {
        return AnomalyGeometry.centeredAabb(pos, size).getCenter();
    }

    /** Plesh / Voronka: inside the pull sphere (measured to the entity's middle). */
    public static boolean inSphere(Entity entity, BlockPos pos, double size) {
        double r = reach(size);
        return entity.getBoundingBox().getCenter().distanceToSqr(center(pos, size)) <= r * r;
    }

    /** Karusel: the cylinder standing on the zone's bottom, {@link #reach} wide and high. */
    public static boolean inCylinder(Entity entity, BlockPos pos, double size) {
        AABB zone = AnomalyGeometry.centeredAabb(pos, size);
        double r = reach(size);
        Vec3 c = zone.getCenter();
        double dx = entity.getX() - c.x;
        double dz = entity.getZ() - c.z;
        double y = entity.getY();
        return dx * dx + dz * dz <= r * r && y >= zone.minY - 0.5 && y <= zone.minY + r;
    }

    public static double horizontalDistance(Entity entity, Vec3 axis) {
        double dx = entity.getX() - axis.x;
        double dz = entity.getZ() - axis.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** What the gravitational anomalies move: living things (not players in creative or
     *  spectators), items, experience orbs and projectiles; never riders or anomaly entities. */
    public static boolean movable(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isPassenger()) return false;
        if (entity instanceof TeslaEntity || entity instanceof ArmorStand) return false;
        if (entity instanceof Player player && player.isCreative()) return false;
        return entity instanceof LivingEntity || entity instanceof ItemEntity
                || entity instanceof ExperienceOrb || entity instanceof Projectile;
    }

    /** A projectile that is really flying (moved this tick) — an arrow stuck in the ground isn't. */
    public static boolean flying(Entity projectile) {
        return projectile.position().distanceToSqr(projectile.xo, projectile.yo, projectile.zo) > 1.0E-4;
    }

    /** Per-tick gravity of an entity (what we cancel while it's held in the air). */
    public static double gravityOf(Entity entity) {
        if (entity.isNoGravity()) return 0.0;
        if (entity instanceof LivingEntity) return 0.08;
        if (entity instanceof Projectile) return 0.03;
        return 0.04;
    }

    // ==== Plesh / Voronka pull ==============================================================

    /** A small orbit around the center, different for every entity, the same on both sides. */
    public record Orbit(Vec3 u, Vec3 w, double radius, double speed, double phase) {
        public static final Orbit STILL = new Orbit(new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.0, 0.0, 0.0);

        public static Orbit of(int anomalySeed, int entityId) {
            RandomSource r = RandomSource.create(anomalySeed * 31L + entityId * 0x9E3779B97F4A7C15L);
            Vec3 n = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6 + 0.6, r.nextGaussian()).normalize();
            Vec3 helper = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 u = n.cross(helper).normalize();
            Vec3 w = n.cross(u).normalize();
            return new Orbit(u, w, 0.35 + r.nextDouble() * 0.35, 0.18 + r.nextDouble() * 0.16, r.nextDouble() * Math.PI * 2.0);
        }

        public Vec3 offset(double ticks) {
            double a = phase + ticks * speed;
            return u.scale(Math.cos(a) * radius).add(w.scale(Math.sin(a) * radius));
        }
    }

    /**
     * New velocity for one tick of a Plesh / Voronka pull: towards the center, faster from further
     * away, smoothed; once caught it follows its {@code orbit} ({@link Orbit#STILL} holds it at the
     * center). Gravity is cancelled, so things float.
     */
    public static Vec3 pull(Vec3 pos, Vec3 velocity, Vec3 center, double force, double gravity, Orbit orbit, double ticks) {
        Vec3 to = center.subtract(pos);
        double d = to.length();
        Vec3 v;
        if (d < CAPTURE_RADIUS) {
            Vec3 target = center.add(orbit.offset(ticks));
            Vec3 want = target.subtract(pos).scale(0.45);
            if (want.length() > 0.6) want = want.normalize().scale(0.6);
            v = velocity.scale(0.3).add(want.scale(0.7));
        } else {
            double speed = Math.min(0.6, 0.12 + 0.09 * d) * force;
            Vec3 want = to.scale(Math.min(speed, d * 0.5) / d);
            v = velocity.scale(0.5).add(want.scale(0.5));
        }
        return v.add(0.0, gravity, 0.0);
    }

    // ==== Karusel swirl ======================================================================

    /**
     * New velocity for one tick of the Karusel: a sideways pull to the axis plus a push along the
     * circle, vertical motion untouched. On the ground the pull is a bit weaker than a sprint, so
     * running flat out gets you away; in the air (no grip) it's stronger.
     */
    public static Vec3 swirl(Vec3 pos, Vec3 velocity, Vec3 axis, double force, boolean onGround) {
        double dx = pos.x - axis.x;
        double dz = pos.z - axis.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1.0E-4) return velocity;
        double ix = -dx / d;
        double iz = -dz / d;
        double tx = -iz;
        double tz = ix;
        double pullAccel = (onGround ? 0.15 : 0.22) * force * Math.min(1.0, d / 0.6);
        double spinTarget = 0.28 * force * Math.min(1.0, d / 0.8);
        double along = velocity.x * tx + velocity.z * tz;
        double spinAccel = (spinTarget - along) * 0.2;
        return new Vec3(velocity.x + ix * pullAccel + tx * spinAccel, velocity.y, velocity.z + iz * pullAccel + tz * spinAccel);
    }

    // ==== Podushka bounce =====================================================================

    /** One entity's bounce in a Podushka: braking a fall, or launching (with its sideways kick). */
    public static final class Bounce {
        public enum Phase { BRAKE, LAUNCH }

        public Phase phase;
        public final double kickX;
        public final double kickZ;
        public boolean kicked;

        public Bounce(Phase phase, RandomSource random) {
            this.phase = phase;
            double angle = random.nextDouble() * Math.PI * 2.0;
            // ~15° off vertical, a little less or more each time.
            double tilt = Math.tan(Math.toRadians(15.0)) * (0.6 + random.nextDouble() * 0.6);
            this.kickX = Math.cos(angle) * tilt;
            this.kickZ = Math.sin(angle) * tilt;
        }
    }

    /**
     * New velocity for one tick inside a Podushka. A fall is braked smoothly; then (or right away
     * when not falling) it accelerates smoothly upwards, and on its way out of the top it gets
     * exactly the speed that carries it {@code height} blocks above the cushion's top, plus a
     * sideways kick of ~15°. Horizontal motion is kept. {@code gravity}: the entity's own per-tick
     * gravity — added back, since the game subtracts it again on the entity's next tick.
     */
    public static Vec3 bounce(Vec3 velocity, double feetY, double top, double height, double gravity, Bounce state) {
        double vy = velocity.y;
        double vx = velocity.x;
        double vz = velocity.z;
        if (state.phase == Bounce.Phase.BRAKE) {
            vy = vy * 0.7 + 0.015;
            if (vy > -0.03) state.phase = Bounce.Phase.LAUNCH;
            return new Vec3(vx, vy + gravity, vz);
        }
        double rise = Math.max(0.1, top + height - feetY);
        double need = Math.sqrt(2.0 * Math.max(0.01, gravity) * rise) * 1.06;
        vy = Math.min(Math.max(vy, 0.0) + 0.15, need);
        // Leaving through the top this tick (a short cushion): full speed now, so it still flies the whole height.
        if (feetY + vy >= top - 0.05) vy = need;
        if (!state.kicked && vy >= need * 0.98) {
            state.kicked = true;
            vx += state.kickX * need;
            vz += state.kickZ * need;
        }
        return new Vec3(vx, vy + gravity, vz);
    }

    /** Sneaking inside a Podushka: sink slowly instead of bouncing. */
    public static Vec3 sink(Vec3 velocity, double gravity) {
        double vy = Math.max(Math.min(velocity.y, 0.0) * 0.8, -0.06);
        return new Vec3(velocity.x, vy + gravity, velocity.z);
    }

    /** Fresh bounce state for something that just got into a Podushka. */
    public static Bounce startBounce(Vec3 velocity, RandomSource random) {
        return new Bounce(velocity.y < -0.08 ? Bounce.Phase.BRAKE : Bounce.Phase.LAUNCH, random);
    }

    // ==== Plesh throw =========================================================================

    /** A throw direction: any way round, at least 30° (at most 65°) above the horizon. */
    public static Vec3 throwDirection(RandomSource random) {
        double azimuth = random.nextDouble() * Math.PI * 2.0;
        double elevation = Math.toRadians(30.0 + random.nextDouble() * 35.0);
        double h = Math.cos(elevation);
        return new Vec3(Math.cos(azimuth) * h, Math.sin(elevation), Math.sin(azimuth) * h);
    }

    /** Linear falloff from 1 at the center to {@code edge} at distance {@code radius}. */
    public static double falloff(double distance, double radius, double edge) {
        double t = Mth.clamp(distance / Math.max(1.0E-4, radius), 0.0, 1.0);
        return 1.0 - (1.0 - edge) * t;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoneArtifacts.MODID, path);
    }
}
