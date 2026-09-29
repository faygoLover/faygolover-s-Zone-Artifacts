package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.item.TeslaRouteToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Server-authoritative "am I looking at a Tesla waypoint" check, independent of the block grid —
 * for the case a waypoint's marker block has already been broken (expected once its route is
 * finished; a Tesla bumps into her own route's blocks until the GM clears them) and the point is now
 * just floating in open air, which {@code RightClickBlock}'s own block raytrace can never find.
 * {@link #pick} checks both the in-progress chain (straight from the held stack's own NBT) and every
 * persisted route in this level, so a right-click on a floating point works exactly like one on a
 * real block still would. {@code AnomalyTargeting}'s counterpart for routes; unlike the left-click /
 * remove case (see {@code TeslaRouteClientTargeting}), right-click reaches the server directly via
 * {@code RightClickItem} whenever there's no real block under the cursor, so no client packet is
 * needed here at all.
 */
public final class TeslaRouteTargeting {

    private static final double REACH = 8.0;

    /** Waypoints are checked as a slightly bigger-than-a-block box, not the exact 1×1×1 cube - a
     *  real click that's a hair off (or a marker block that isn't a full cube to begin with, like a
     *  carpet or a slab) still counts as hitting the point, matching what the highlight box shows. */
    private static final double HIT_INFLATE = 0.25;

    private TeslaRouteTargeting() {
    }

    public static Optional<BlockPos> pick(ServerLevel level, Player player) {
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

        for (TeslaRoute route : TeslaSavedData.get(level).routes()) {
            for (BlockPos pos : route.waypoints()) {
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
