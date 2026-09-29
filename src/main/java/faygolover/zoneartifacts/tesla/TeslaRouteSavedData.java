package faygolover.zoneartifacts.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-dimension persistence for <em>completed</em> Tesla routes. Drafts that are still being built
 * never land here — they live only in memory ({@link TeslaDrafts}) and vanish on logout,
 * dimension change and server stop, exactly as specified.
 */
public class TeslaRouteSavedData extends SavedData {

    private static final String ID = "fl_zone_arts_tesla_routes";

    private final Map<Integer, TeslaRoute> routes = new LinkedHashMap<>();
    private int nextId = 1;

    public static TeslaRouteSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TeslaRouteSavedData::load, TeslaRouteSavedData::new, ID);
    }

    public Collection<TeslaRoute> routes() {
        return routes.values();
    }

    @Nullable
    public TeslaRoute get(int id) {
        return routes.get(id);
    }

    public TeslaRoute create(List<BlockPos> waypoints) {
        TeslaRoute route = new TeslaRoute(nextId++, waypoints);
        routes.put(route.id(), route);
        setDirty();
        return route;
    }

    @Nullable
    public TeslaRoute remove(int id) {
        TeslaRoute removed = routes.remove(id);
        if (removed != null) setDirty();
        return removed;
    }

    public static TeslaRouteSavedData load(CompoundTag tag) {
        TeslaRouteSavedData data = new TeslaRouteSavedData();
        ListTag list = tag.getList("routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            TeslaRoute route = TeslaRoute.load(list.getCompound(i));
            data.routes.put(route.id(), route);
        }
        data.nextId = Math.max(tag.getInt("next_id"), 1);
        for (int id : data.routes.keySet()) {
            data.nextId = Math.max(data.nextId, id + 1);
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
        tag.putInt("next_id", nextId);
        return tag;
    }
}
