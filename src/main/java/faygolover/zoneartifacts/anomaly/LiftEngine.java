package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server side of the Lift ({@link Lift}): always on, harmless. Moves mobs, items and projectiles
 * (players move themselves, client-side), counts how long each has been inside, keeps fall damage
 * off inside and for a few seconds after leaving.
 */
public final class LiftEngine {

    private static final Map<AnomalyInstance, Map<UUID, Integer>> INSIDE = new WeakHashMap<>();

    private LiftEngine() {
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        double height = ModCommonConfig.LIFT_HOVER_HEIGHT.get() * instance.speed();
        int pushOut = AnomalyDefaults.ticks(ModCommonConfig.LIFT_PUSH_OUT_SECONDS.get());
        Map<UUID, Integer> inside = INSIDE.computeIfAbsent(instance, k -> new HashMap<>());
        Map<UUID, Integer> seen = new HashMap<>();

        for (Entity e : level.getEntities((Entity) null, zone, Lift::affects)) {
            int t = inside.getOrDefault(e.getUUID(), 0) + 1;
            seen.put(e.getUUID(), t);
            if (e instanceof LivingEntity living) living.resetFallDistance();
            if (e instanceof Player) continue; // its own client floats it
            if (e instanceof Projectile) e.getPersistentData().putBoolean(Lift.STUCK_TAG, true);
            double target = Lift.targetFeetY(level, e, zone, height, 0.0);
            e.setDeltaMovement(Lift.apply(e, zone, target, t, pushOut));
            e.hasImpulse = true;
        }

        long now = level.getGameTime();
        for (Iterator<Map.Entry<UUID, Integer>> it = inside.entrySet().iterator(); it.hasNext(); ) {
            UUID id = it.next().getKey();
            if (seen.containsKey(id)) continue;
            it.remove();
            Entity left = level.getEntity(id);
            if (left instanceof LivingEntity living && living.isAlive() && !living.onGround()) {
                GravityEngine.softenFall(living, now + 100, 0.0);
            }
            if (left instanceof Projectile) left.getPersistentData().remove(Lift.STUCK_TAG);
        }
        inside.putAll(seen);
    }

    /** The Lift was removed. */
    public static void forget(AnomalyInstance instance) {
        INSIDE.remove(instance);
    }
}
