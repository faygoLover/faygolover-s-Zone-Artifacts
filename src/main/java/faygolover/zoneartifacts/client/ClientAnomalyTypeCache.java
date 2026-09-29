package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket.AmbientSoundInfo;
import faygolover.zoneartifacts.network.SyncAnomalyTypeShapesPacket.ArcInfo;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Client-side copy of each loaded anomaly type's per-level sizes, ambient sound and arc-visual
 * config — everything the client needs to draw a preview box and run the looping idle sound (see
 * {@code AnomalyAmbientSoundHandler}) and lightning arcs (see {@code AnomalyArcRenderer}) itself.
 * Synced once on login (see {@code SyncAnomalyTypeShapesPacket}); the rest of an AnomalyType
 * (damage, detect rules, effects...) is server-only and never needs to leave it.
 * <p>
 * Known stage-1 limitation: this doesn't currently re-sync after a live {@code /reload} while a
 * client is already connected — only on (re)join. Fine for now since datapack edits during a
 * stage-1 test session are rare; worth adding a resync-on-reload hook later if that changes.
 */
public final class ClientAnomalyTypeCache {

    private static Map<ResourceLocation, List<Integer>> sizesByLevelByType = Map.of();
    private static Map<ResourceLocation, AmbientSoundInfo> ambientSoundByType = Map.of();
    private static Map<ResourceLocation, ArcInfo> arcByType = Map.of();

    public static void set(Map<ResourceLocation, List<Integer>> sizes, Map<ResourceLocation, AmbientSoundInfo> ambientSounds,
                            Map<ResourceLocation, ArcInfo> arcs) {
        sizesByLevelByType = sizes;
        ambientSoundByType = ambientSounds;
        arcByType = arcs;
    }

    public static int sizeForLevel(ResourceLocation typeId, int level) {
        List<Integer> sizes = sizesByLevelByType.get(typeId);
        if (sizes == null || sizes.isEmpty()) return 1;
        int index = Math.max(1, Math.min(level, sizes.size())) - 1;
        return sizes.get(index);
    }

    @Nullable
    public static AmbientSoundInfo ambientSoundFor(ResourceLocation typeId) {
        return ambientSoundByType.get(typeId);
    }

    @Nullable
    public static ArcInfo arcFor(ResourceLocation typeId) {
        return arcByType.get(typeId);
    }

    private ClientAnomalyTypeCache() {
    }
}
