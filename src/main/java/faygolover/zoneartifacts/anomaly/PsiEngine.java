package faygolover.zoneartifacts.anomaly;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the psi zone: mobs don't like it there — not afraid, they just walk out at their
 * usual pace (and not back in on their own, since they keep being steered out). What it does to
 * players is all their client's (their senses).
 */
public final class PsiEngine {

    private PsiEngine() {
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        if ((level.getGameTime() + instance.pos().hashCode()) % 20 != 0) return;
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        Vec3 c = zone.getCenter();
        int reach = (int) Math.ceil(instance.size() / 2.0) + 4;
        for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, zone, m -> m.isAlive() && !m.isPassenger() && !m.isNoAi())) {
            if (!mob.getNavigation().isDone()) {
                // Already on its way — out, hopefully: only redirect if its target is inside.
                var target = mob.getNavigation().getTargetPos();
                if (target == null || !zone.contains(Vec3.atCenterOf(target))) continue;
            }
            Vec3 away = DefaultRandomPos.getPosAway(mob, reach, 4, c);
            if (away == null) {
                Vec3 dir = mob.position().subtract(c);
                dir = new Vec3(dir.x, 0.0, dir.z);
                if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(1.0, 0.0, 0.0);
                away = mob.position().add(dir.normalize().scale(reach));
            }
            mob.getNavigation().moveTo(away.x, away.y, away.z, 0.9);
        }
    }
}
