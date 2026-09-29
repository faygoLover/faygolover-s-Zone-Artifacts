package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.item.TeslaRouteToolItem;
import faygolover.zoneartifacts.network.SyncTeslaRoutesPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Client-side, non-authoritative "am I looking at a Tesla waypoint" check, used only for the
 * left-click / remove-point fallback (see {@code ClientTeslaRouteInputHandler}), since
 * {@code LeftClickEmpty} is a client-only event with no server-side equivalent to hook instead
 * (compare {@code TeslaRouteTargeting}, which does the same check server-side for right-click). Runs
 * once vanilla's own block raytrace already came back empty — the case where a waypoint's marker
 * block has since been broken (expected once a route is finished; a Tesla bumps into her own route's
 * blocks until the GM clears them, per the route tool's own design) and the point is now just
 * floating in open air, which a real block raytrace can never find. Checks both the in-progress
 * chain (read straight from the held stack's own NBT — no packet needed for that half) and every
 * nearby synced route (see {@link ClientTeslaRouteCache}). Mirrors {@code AnomalyClientTargeting}
 * exactly; the server always re-validates by exact position before acting (see {@code
 * TeslaRouteClickPacket}), so this is only ever used to decide what to click, never to decide what
 * happens.
 */
public final class TeslaRouteClientTargeting {

    private static final double REACH = 8.0;

    /** Matches {@code TeslaRouteTargeting}'s own margin - see its javadoc for why. */
    private static final double HIT_INFLATE = 0.25;

    private TeslaRouteClientTargeting() {
    }

    public static Optional<BlockPos> pick(Player player) {
        ItemStack stack = TeslaRouteToolItem.heldStack(player);
        if (stack == null) return Optional.empty();

        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getViewVector(1.0f).scale(REACH));

        BlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (BlockPos pos : TeslaRouteToolItem.getChain(stack)) {
            Optional<Vec3> hit = new AABB(pos).inflate(HIT_INFLATE).clip(eye, reachEnd);
            if (hit.isEmpty()) continue;
            double distSq = eye.distanceToSqr(hit.get());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = pos;
            }
        }

        for (SyncTeslaRoutesPacket.Entry entry : ClientTeslaRouteCache.entriesFor(player.level().dimension())) {
            for (BlockPos pos : entry.waypoints()) {
                Optional<Vec3> hit = new AABB(pos).inflate(HIT_INFLATE).clip(eye, reachEnd);
                if (hit.isEmpty()) continue;
                double distSq = eye.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = pos;
                }
            }
        }

        return Optional.ofNullable(closest);
    }
}
