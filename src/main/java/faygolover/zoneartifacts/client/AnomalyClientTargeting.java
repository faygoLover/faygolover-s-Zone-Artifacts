package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Client-side mirror of {@code AnomalyTargeting}: the same ray/AABB-clip logic, run against the
 * client's own {@link ClientAnomalyCache} mirror instead of the server's real data. Purely for
 * rendering feedback (which anomaly to highlight, what a click would hit) — never trusted for
 * anything that actually changes state; the server always re-resolves the real target itself.
 */
public final class AnomalyClientTargeting {

    private static final double REACH = 6.0;

    private AnomalyClientTargeting() {
    }

    public static Optional<ClientAnomalyCache.Entry> pick(ResourceLocation typeId) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return Optional.empty();

        SyncAnomalyTypeShapesPacket.TypeShape shape = ClientAnomalyTypeCache.get(typeId);
        if (shape == null) return Optional.empty();

        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getViewVector(1.0f).scale(REACH));

        ClientAnomalyCache.Entry closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (ClientAnomalyCache.Entry entry : ClientAnomalyCache.ofType(typeId)) {
            int size = ClientAnomalyTypeCache.sizeForLevel(shape, entry.level());
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
