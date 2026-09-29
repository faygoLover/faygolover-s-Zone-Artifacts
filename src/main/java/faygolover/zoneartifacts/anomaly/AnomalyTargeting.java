package faygolover.zoneartifacts.anomaly;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Server-side "am I looking at an anomaly" check, independent of the block grid — a zone isn't a
 * block, so vanilla's block raytrace can't find it. Runs against the server's own
 * {@link AnomalySavedData}, so a click is never trusted beyond "yes, interact" / "no, don't".
 */
public final class AnomalyTargeting {

    /** Generous reach for interacting with a volume rather than a block face. */
    public static final double REACH = 6.0;

    private AnomalyTargeting() {
    }

    /** Nearest anomaly zone along the player's view ray; {@code typeId == null} means any type. */
    public static Optional<AnomalyInstance> pick(ServerLevel level, Player player, @Nullable ResourceLocation typeId) {
        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getViewVector(1.0f).scale(REACH));

        AnomalyInstance closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (AnomalyInstance instance : List.copyOf(AnomalySavedData.get(level).instances())) {
            if (typeId != null && !instance.typeId().equals(typeId)) continue;

            AABB aabb = AnomalyGeometry.zoneAabb(instance);
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
