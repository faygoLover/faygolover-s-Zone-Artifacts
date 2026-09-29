package faygolover.zoneartifacts.anomaly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-dimension persistence for placed anomalies. One instance of this per {@link ServerLevel},
 * lazily created/loaded on first access via {@link #get(ServerLevel)}.
 */
public class AnomalySavedData extends SavedData {

    private static final String ID = "fl_zone_arts_anomalies";

    private final List<AnomalyInstance> instances = new ArrayList<>();

    public static AnomalySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(AnomalySavedData::load, AnomalySavedData::new, ID);
    }

    public List<AnomalyInstance> instances() {
        return instances;
    }

    public void add(AnomalyInstance instance) {
        instances.add(instance);
        setDirty();
    }

    public void remove(AnomalyInstance instance) {
        instances.remove(instance);
        setDirty();
    }

    public static AnomalySavedData load(CompoundTag tag) {
        AnomalySavedData data = new AnomalySavedData();
        ListTag list = tag.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.instances.add(AnomalyInstance.load(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (AnomalyInstance instance : instances) {
            list.add(instance.save());
        }
        tag.put("instances", list);
        return tag;
    }
}
