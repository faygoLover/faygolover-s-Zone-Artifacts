package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Every {@link #CHECK_TICKS} ticks each route checks that its Tesla / Comet / Gravi still exists —
 * it can be killed with a command. If its last known spot is loaded and it isn't there, a new one
 * is spawned on the route (only where the route's first point is loaded too; an anomaly sitting in
 * an unloaded chunk is simply not looked for).
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class TeslaRouteWatchdog {

    private static final int CHECK_TICKS = 600;

    private TeslaRouteWatchdog() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % CHECK_TICKS != 17) return;
        TeslaRouteSavedData data = TeslaRouteSavedData.get(level);
        boolean changed = false;
        for (TeslaRoute route : List.copyOf(data.routes())) {
            List<BlockPos> points = route.waypoints();
            if (points.isEmpty()) continue;
            if (route.teslaUuid() != null) {
                Entity existing = level.getEntity(route.teslaUuid());
                if (existing != null && existing.isAlive()) continue;
                BlockPos last = route.lastKnownTeslaPos() != null ? route.lastKnownTeslaPos() : points.get(0);
                if (!entitiesLoaded(level, last)) continue; // may just be in an unloaded chunk
            }
            if (!entitiesLoaded(level, points.get(0))) continue;
            TeslaEntity entity = route.kind().entityType().create(level);
            if (entity == null) continue;
            entity.initOnRoute(route);
            level.addFreshEntity(entity);
            route.setTeslaUuid(entity.getUUID());
            route.setLastKnownTeslaPos(entity.blockPosition());
            changed = true;
        }
        if (changed) data.setDirty();
    }

    private static boolean entitiesLoaded(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.areEntitiesLoaded(ChunkPos.asLong(pos));
    }
}
