package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.Optional;

/**
 * Client-side mirror of the server's {@code AnomalyTargeting} raytrace, run against the synced
 * {@link ClientAnomalyCache} instead of the real (server-only) saved data. Used only to decide
 * what to render and whether to send a {@code RemoveAnomalyPacket} — never authoritative; the
 * server always re-resolves the real target by position before changing anything.
 */
public final class AnomalyClientTargeting {

    private static final double REACH = 6.0;

    /** Squared distance from the eye to where the view ray enters {@code entry}'s zone, if it does. */
    public static Optional<Double> hitDistanceSq(Player player, SyncAnomaliesPacket.Entry entry) {
        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getViewVector(1.0f).scale(REACH));
        return AnomalyGeometry.zoneAabb(entry).clip(eye, reachEnd).map(eye::distanceToSqr);
    }

    private AnomalyClientTargeting() {
    }

    /** Nearest synced anomaly zone along the player's view ray; {@code typeId == null} means any type. */
    public static Optional<SyncAnomaliesPacket.Entry> pick(Player player, @Nullable ResourceLocation typeId) {
        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getViewVector(1.0f).scale(REACH));

        SyncAnomaliesPacket.Entry closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.allEntriesFor(player.level().dimension())) {
            if (typeId != null && !entry.typeId().equals(typeId)) continue;

            AABB aabb = AnomalyGeometry.zoneAabb(entry);
            Optional<Vec3> hit = aabb.clip(eye, reachEnd);
            if (hit.isEmpty()) continue;

            double distSq = eye.distanceToSqr(hit.get());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = entry;
            }
        }

        return Optional.ofNullable(closest);
    }
}
