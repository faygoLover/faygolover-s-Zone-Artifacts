package faygolover.zoneartifacts.client;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Client-side copy of each loaded anomaly type's per-level sizes — all the client needs to draw
 * a preview box. Synced once on login (see {@code SyncAnomalyTypeShapesPacket}); the rest of an
 * AnomalyType (damage, detect rules, effects...) is server-only and never needs to leave it.
 * <p>
 * Known stage-1 limitation: this doesn't currently re-sync after a live {@code /reload} while a
 * client is already connected — only on (re)join. Fine for now since datapack edits during a
 * stage-1 test session are rare; worth adding a resync-on-reload hook later if that changes.
 */
public final class ClientAnomalyTypeCache {

    private static Map<ResourceLocation, List<Integer>> sizesByLevelByType = Map.of();

    public static void set(Map<ResourceLocation, List<Integer>> data) {
        sizesByLevelByType = data;
    }

    public static int sizeForLevel(ResourceLocation typeId, int level) {
        List<Integer> sizes = sizesByLevelByType.get(typeId);
        if (sizes == null || sizes.isEmpty()) return 1;
        int index = Math.max(1, Math.min(level, sizes.size())) - 1;
        return sizes.get(index);
    }

    private ClientAnomalyTypeCache() {
    }
}
