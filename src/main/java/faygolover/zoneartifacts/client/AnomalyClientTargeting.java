package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Client-side mirror of the server's {@code AnomalyTargeting} raytrace, run against the synced
 * {@link ClientAnomalyCache} instead of the real (server-only) saved data. Used only to decide
 * what to render and whether to send a {@code RemoveAnomalyPacket} — never authoritative; the
 * server always re-resolves the real target by position before changing anything.
 */
public final class AnomalyClientTargeting {

    private static final double REACH = 6.0;

    private AnomalyClientTargeting() {
    }

    public static Optional<SyncAnomaliesPacket.Entry> pick(Player player, ResourceLocation typeId) {
        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getViewVector(1.0f).scale(REACH));

        SyncAnomaliesPacket.Entry closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (SyncAnomaliesPacket.Entry entry : ClientAnomalyCache.entriesFor(player.level().dimension())) {
            if (!entry.typeId().equals(typeId)) continue;

            int size = ClientAnomalyTypeCache.sizeForLevel(entry.typeId(), entry.level());
            AABB aabb = AnomalyGeometry.centeredAabb(entry.pos(), size);
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
