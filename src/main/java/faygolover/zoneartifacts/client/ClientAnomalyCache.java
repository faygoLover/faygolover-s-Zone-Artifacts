package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.network.SyncAnomaliesPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side mirror of every placed anomaly. Kept per-dimension: a stray delta packet for a
 * dimension the player has already left is simply ignored rather than needing to be filtered at
 * the network layer.
 */
public final class ClientAnomalyCache {

    private static ResourceLocation currentDimension = null;
    private static final Map<Key, Entry> ENTRIES = new HashMap<>();
    private static final Set<Key> ON_COOLDOWN = new HashSet<>();

    private ClientAnomalyCache() {
    }

    public static void replaceAll(ResourceLocation dimension, List<SyncAnomaliesPacket.Entry> entries) {
        currentDimension = dimension;
        ENTRIES.clear();
        ON_COOLDOWN.clear();
        for (SyncAnomaliesPacket.Entry entry : entries) {
            Entry local = new Entry(entry.typeId(), entry.pos(), entry.level());
            ENTRIES.put(local.key(), local);
        }
    }

    public static void remove(ResourceLocation dimension, ResourceLocation typeId, BlockPos pos) {
        if (!dimension.equals(currentDimension)) return;
        Key key = new Key(typeId, pos);
        ENTRIES.remove(key);
        ON_COOLDOWN.remove(key);
    }

    public static void setCooldown(ResourceLocation dimension, ResourceLocation typeId, BlockPos pos, boolean onCooldown) {
        if (!dimension.equals(currentDimension)) return;
        Key key = new Key(typeId, pos);
        if (onCooldown) {
            ON_COOLDOWN.add(key);
        } else {
            ON_COOLDOWN.remove(key);
        }
    }

    public static boolean isOnCooldown(ResourceLocation typeId, BlockPos pos) {
        return ON_COOLDOWN.contains(new Key(typeId, pos));
    }

    public static List<Entry> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static List<Entry> ofType(ResourceLocation typeId) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : ENTRIES.values()) {
            if (entry.typeId().equals(typeId)) result.add(entry);
        }
        return result;
    }

    public record Key(ResourceLocation typeId, BlockPos pos) {
    }

    public record Entry(ResourceLocation typeId, BlockPos pos, int level) {
        public Key key() {
            return new Key(typeId, pos);
        }
    }
}
