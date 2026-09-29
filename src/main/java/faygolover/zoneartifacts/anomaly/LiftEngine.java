package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Lift ({@link Lift}): always on, harmless. Moves mobs, items and projectiles
 * (players move themselves, client-side) and counts how long each has been inside. Stacked Lifts
 * act as one ({@link Lift#column}): everything is moved once per tick, whichever of them gets to it
 * first, and the time inside counts across them.
 */
public final class LiftEngine {

    private record Inside(int ticks, long lastSeen) {
    }

    private static final Map<UUID, Inside> INSIDE = new HashMap<>();
    private static long zonesTick = -1;
    private static List<AABB> zones = List.of();

    private LiftEngine() {
    }

    private static List<AABB> zones(ServerLevel level) {
        long now = level.getGameTime();
        if (now != zonesTick) {
            List<AABB> list = new ArrayList<>();
            for (AnomalyInstance other : AnomalySavedData.get(level).instances()) {
                if (AnomalyTypeIds.LIFT.equals(other.typeId())) list.add(AnomalyGeometry.zoneAabb(other));
            }
            zones = list;
            zonesTick = now;
        }
        return zones;
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        double height = ModCommonConfig.LIFT_HOVER_HEIGHT.get();
        int pushOut = AnomalyDefaults.ticks(ModCommonConfig.LIFT_PUSH_OUT_SECONDS.get());
        long now = level.getGameTime();

        for (Entity e : level.getEntities((Entity) null, zone, Lift::affects)) {
            Inside was = INSIDE.get(e.getUUID());
            if (was != null && was.lastSeen() == now) continue; // another Lift of the stack did it
            int t = was != null && was.lastSeen() >= now - 1 ? was.ticks() + 1 : 1;
            INSIDE.put(e.getUUID(), new Inside(t, now));
            if (e instanceof LivingEntity living) living.resetFallDistance();
            if (e instanceof Player) continue; // its own client floats it
            if (e instanceof Projectile) e.getPersistentData().putBoolean(Lift.STUCK_TAG, true);
            AABB column = Lift.column(zones(level), e.getBoundingBox());
            if (column == null) column = zone;
            double target = Lift.targetFeetY(level, e, column, height, 0.0);
            e.setDeltaMovement(Lift.apply(e, zone, target, t, pushOut, instance.speed()));
            e.hasImpulse = true;
        }

        if (now % 20 == 0) {
            for (Iterator<Map.Entry<UUID, Inside>> it = INSIDE.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Inside> entry = it.next();
                if (entry.getValue().lastSeen() >= now - 2) continue;
                it.remove();
                if (level.getEntity(entry.getKey()) instanceof Projectile left) left.getPersistentData().remove(Lift.STUCK_TAG);
            }
        }
    }

    /** The Lift was removed. */
    public static void forget(AnomalyInstance instance) {
        zonesTick = -1;
    }
}
