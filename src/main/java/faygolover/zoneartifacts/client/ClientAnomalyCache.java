package faygolover.zoneartifacts.client;

import faygolover.zoneartifacts.network.SyncAnomalyCooldownPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import faygolover.zoneartifacts.network.SyncAnomaliesPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side snapshot of "what anomalies exist and where", refreshed wholesale whenever a
 * {@link SyncAnomaliesPacket} arrives (join, dimension change, or any placement/removal/level
 * change) — or patched in place, one entry at a time, whenever a lighter-weight {@link
 * SyncAnomalyCooldownPacket} arrives (see {@link #updateState}). Purely a rendering aid for
 * {@code AnomalyHighlightRenderer} — never authoritative, the server always re-checks for real
 * before acting on a click (see AnomalyTargeting).
 */
public final class ClientAnomalyCache {

    private static ResourceKey<Level> dimension = null;
    private static List<SyncAnomaliesPacket.Entry> entries = List.of();

    public static void set(ResourceKey<Level> dim, List<SyncAnomaliesPacket.Entry> newEntries) {
        dimension = dim;
        entries = newEntries;
    }

    /** Patches a single entry's cooldown / active flags in place, without touching anything else
     *  about it — the cheap counterpart to a full {@link #set}, driven by {@link SyncAnomalyCooldownPacket}. */
    public static void updateState(ResourceLocation typeId, BlockPos pos, boolean onCooldown, boolean active) {
        for (int i = 0; i < entries.size(); i++) {
            SyncAnomaliesPacket.Entry entry = entries.get(i);
            if (entry.typeId().equals(typeId) && entry.pos().equals(pos)) {
                if (entry.onCooldown() == onCooldown && entry.active() == active) return;
                List<SyncAnomaliesPacket.Entry> updated = new ArrayList<>(entries);
                updated.set(i, new SyncAnomaliesPacket.Entry(entry.typeId(), entry.pos(), entry.size(), entry.intensity(), onCooldown, active, entry.speed(), entry.cooldown(), entry.damage()));
                entries = updated;
                return;
            }
        }
    }

    public static List<SyncAnomaliesPacket.Entry> entriesFor(ResourceKey<Level> dim) {
        return dim.equals(dimension) ? entries : List.of();
    }

    private ClientAnomalyCache() {
    }
}
