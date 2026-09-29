package faygolover.zoneartifacts.anomaly;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Server-authoritative "am I looking at an anomaly" check, independent of the block grid — an
 * anomaly's zone isn't a block, so vanilla's own block raytrace can't find it. This runs entirely
 * server-side against the server's own {@link AnomalySavedData}, so a player's click is never
 * trusted for anything beyond "yes, interact" / "no, don't" (the same way vanilla resolves what
 * block you're mining from your position + look direction).
 */
public final class AnomalyTargeting {

    /** Generous reach for interacting with a volume rather than a block face. */
    private static final double REACH = 6.0;

    private AnomalyTargeting() {
    }

    public static Optional<AnomalyInstance> pick(ServerLevel level, Player player, ResourceLocation typeId) {
        AnomalyType type = AnomalyTypeManager.get(typeId);
        if (type == null) return Optional.empty();

        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getViewVector(1.0f).scale(REACH));

        AnomalySavedData data = AnomalySavedData.get(level);
        AnomalyInstance closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (AnomalyInstance instance : List.copyOf(data.instances())) {
            if (!instance.typeId().equals(typeId)) continue;

            AABB aabb = AnomalyGeometry.zoneAabb(instance, type);
            Optional<Vec3> hit = aabb.clip(eye, reachEnd);
            if (hit.isEmpty()) continue;

            double distSq = eye.distanceToSqr(hit.get());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = instance;
            }
        }

        return Optional.ofNullable(closest);
    }
}
