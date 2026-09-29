package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Lift's physics, shared by the server (mobs, items, projectiles) and each client (its own
 * player, whose movement it owns). Gravity is off: everything floats to a hover height above the
 * ground (a jump rises, sneaking sinks, letting go drifts back), the air is thick (any motion dies
 * away fast, your own steps too), projectiles get stuck in it, and after a while inside anything
 * is eased out towards the edge.
 * <p>
 * Works in "this tick's move": a living thing and a projectile subtract gravity after they move,
 * an item before — {@link #lastMoveY} and {@link #toDelta} handle both.
 */
public final class Lift {

    public static final ResourceLocation TYPE = AnomalyTypeIds.LIFT;
    /** Extra drag on horizontal motion every tick (on top of the air's own). */
    public static final double VISCOSITY = 0.93;
    /** Projectiles stall fast. */
    public static final double PROJECTILE_DRAG = 0.8;
    /** Outward push per tick once the time inside is up. */
    public static final double PUSH_OUT = 0.012;
    /** How fast a jump / sneaking moves the hover point, blocks per tick. */
    public static final double CONTROL_SPEED = 0.05;

    private Lift() {
    }

    /** Where the feet want to be: {@code height} above the ground under them (plus {@code offset}),
     *  kept inside the zone. No ground below: stay at the current height. */
    public static double targetFeetY(BlockGetter level, Entity e, AABB zone, double height, double offset) {
        Double ground = Razlom.groundY(level, e.getX(), e.getZ(), e.getY() + 0.5, zone.minY - 4.0);
        double base = ground != null ? ground : e.getY();
        double top = zone.maxY - 0.3;
        return Mth.clamp(base + height + offset, base, Math.max(base, top));
    }

    /**
     * New delta movement for one tick inside. {@code ticksInside}: how long it's been in;
     * {@code pushOutTicks}: after that it's eased out towards the edge.
     */
    public static Vec3 apply(Entity e, AABB zone, double targetFeetY, int ticksInside, int pushOutTicks) {
        Vec3 d = e.getDeltaMovement();
        double g = Gravity.gravityOf(e);
        double hx = d.x;
        double hz = d.z;
        double moveY;
        if (e instanceof Projectile) {
            // Stuck: stalls and hangs where it is.
            hx *= PROJECTILE_DRAG;
            hz *= PROJECTILE_DRAG;
            moveY = lastMoveY(e, d.y) * PROJECTILE_DRAG;
        } else {
            hx *= VISCOSITY;
            hz *= VISCOSITY;
            double pull = Mth.clamp((targetFeetY - e.getY()) * 0.05, -0.05, 0.05);
            moveY = lastMoveY(e, d.y) * 0.85 + pull;
        }
        if (ticksInside > pushOutTicks) {
            Vec3 c = zone.getCenter();
            double dx = e.getX() - c.x;
            double dz = e.getZ() - c.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-3) {
                dx = Math.cos(e.getId());
                dz = Math.sin(e.getId());
                len = 1.0;
            }
            hx += dx / len * PUSH_OUT;
            hz += dz / len * PUSH_OUT;
        }
        return new Vec3(hx, toDelta(e, moveY, g), hz);
    }

    /** The vertical move of the last tick, recovered from what the game left in the delta. */
    public static double lastMoveY(Entity e, double storedY) {
        if (e.isNoGravity()) return storedY;
        double g = Gravity.gravityOf(e);
        if (e instanceof LivingEntity) return storedY / 0.98 + g;
        if (e instanceof Projectile) return (storedY + g) / 0.99;
        return storedY / 0.98;
    }

    /** The delta to set so the entity moves {@code moveY} this tick. */
    public static double toDelta(Entity e, double moveY, double g) {
        if (e.isNoGravity() || e instanceof LivingEntity || e instanceof Projectile) return moveY;
        return moveY + g; // items and the like lose their gravity before they move
    }

    /** What the Lift moves: like the gravitational anomalies, but projectiles only while in flight. */
    public static boolean affects(Entity e) {
        if (!Gravity.movable(e)) return false;
        return !(e instanceof Projectile) || Gravity.flying(e) || e.getPersistentData().getBoolean(STUCK_TAG);
    }

    /** Set on a projectile the Lift caught, so it stays affected once it has stopped. */
    public static final String STUCK_TAG = ZoneArtifacts.MODID + "_lift_stuck";
}
