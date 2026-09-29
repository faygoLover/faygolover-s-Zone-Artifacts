package faygolover.zoneartifacts.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-dimension persistence for finalized Tesla waypoint routes. A route here always corresponds
 * to a completed build (see {@code TeslaBuildManager} for the separate, never-persisted
 * in-progress session state) and, under normal operation, to one live {@link
 * faygolover.zoneartifacts.tesla.TeslaEntity} — {@code teslaEntityUuid} is what lets the admin
 * cleanup command and the interaction handler tell a healthy route from an orphaned one.
 */
public class TeslaRouteSavedData extends SavedData {

    private static final String ID = "fl_zone_arts_tesla_routes";

    private final Map<UUID, TeslaRoute> routes = new LinkedHashMap<>();

    public static TeslaRouteSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TeslaRouteSavedData::load, TeslaRouteSavedData::new, ID);
    }

    public Map<UUID, TeslaRoute> routes() {
        return routes;
    }

    public void add(TeslaRoute route) {
        routes.put(route.routeId(), route);
        setDirty();
    }

    public void remove(UUID routeId) {
        if (routes.remove(routeId) != null) {
            setDirty();
        }
    }

    public void updateTeslaUuid(UUID routeId, @Nullable UUID teslaEntityUuid) {
        TeslaRoute existing = routes.get(routeId);
        if (existing == null) return;
        routes.put(routeId, existing.withTeslaUuid(teslaEntityUuid));
        setDirty();
    }

    public static TeslaRouteSavedData load(CompoundTag tag) {
        TeslaRouteSavedData data = new TeslaRouteSavedData();
        ListTag list = tag.getList("routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            TeslaRoute route = TeslaRoute.load(list.getCompound(i));
            data.routes.put(route.routeId(), route);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (TeslaRoute route : routes.values()) {
            list.add(route.save());
        }
        tag.put("routes", list);
        return tag;
    }

    /** One finalized Tesla waypoint route. Immutable — {@link #withTeslaUuid} returns a copy. */
    public record TeslaRoute(UUID routeId, List<BlockPos> points, @Nullable UUID teslaEntityUuid) {

        public TeslaRoute withTeslaUuid(@Nullable UUID newTeslaUuid) {
            return new TeslaRoute(routeId, points, newTeslaUuid);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("route_id", routeId);
            if (teslaEntityUuid != null) {
                tag.putUUID("tesla_uuid", teslaEntityUuid);
            }
            ListTag pointList = new ListTag();
            for (BlockPos p : points) {
                CompoundTag pTag = new CompoundTag();
                pTag.putInt("x", p.getX());
                pTag.putInt("y", p.getY());
                pTag.putInt("z", p.getZ());
                pointList.add(pTag);
            }
            tag.put("points", pointList);
            return tag;
        }

        public static TeslaRoute load(CompoundTag tag) {
            UUID routeId = tag.getUUID("route_id");
            UUID teslaUuid = tag.contains("tesla_uuid") ? tag.getUUID("tesla_uuid") : null;
            List<BlockPos> points = new ArrayList<>();
            ListTag pointList = tag.getList("points", Tag.TAG_COMPOUND);
            for (int i = 0; i < pointList.size(); i++) {
                CompoundTag pTag = pointList.getCompound(i);
                points.add(new BlockPos(pTag.getInt("x"), pTag.getInt("y"), pTag.getInt("z")));
            }
            return new TeslaRoute(routeId, List.copyOf(points), teslaUuid);
        }
    }
}
