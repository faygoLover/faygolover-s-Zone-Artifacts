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
        refresh();
    }

    /** Patches a single entry's cooldown / active flags in place, without touching anything else
     *  about it — the cheap counterpart to a full {@link #set}, driven by {@link SyncAnomalyCooldownPacket}. */
    public static void updateState(ResourceLocation typeId, BlockPos pos, boolean onCooldown, boolean active) {
        for (int i = 0; i < entries.size(); i++) {
            SyncAnomaliesPacket.Entry entry = entries.get(i);
            if (entry.typeId().equals(typeId) && entry.pos().equals(pos)) {
                if (entry.onCooldown() == onCooldown && entry.active() == active) return;
                List<SyncAnomaliesPacket.Entry> updated = new ArrayList<>(entries);
                updated.set(i, entry.withState(onCooldown, active));
                entries = updated;
                refresh();
                return;
            }
        }
    }

    /** The working anomalies (switched-off ones are left out: to everything client-side they aren't there). */
    public static List<SyncAnomaliesPacket.Entry> entriesFor(ResourceKey<Level> dim) {
        return dim.equals(dimension) ? working : List.of();
    }

    /** All of them, switched off too — for the KPK and the placers' outlines. */
    public static List<SyncAnomaliesPacket.Entry> allEntriesFor(ResourceKey<Level> dim) {
        return dim.equals(dimension) ? entries : List.of();
    }

    /** Is it drawn (the KPK's "visible" switch)? Its effects on whoever is in it stay either way. */
    public static boolean shown(SyncAnomaliesPacket.Entry entry) {
        return entry.visible();
    }

    private static List<SyncAnomaliesPacket.Entry> working = List.of();
    /** The zones set invisible in the KPK (a little larger: what they give off at their edge goes too). */
    private static List<net.minecraft.world.phys.AABB> hidden = List.of();

    private static void refresh() {
        List<SyncAnomaliesPacket.Entry> out = new ArrayList<>(entries.size());
        List<net.minecraft.world.phys.AABB> boxes = new ArrayList<>();
        for (SyncAnomaliesPacket.Entry e : entries) {
            if (e.enabled()) out.add(e);
            if (e.enabled() && !e.visible()) boxes.add(faygolover.zoneartifacts.anomaly.AnomalyGeometry.zoneAabb(e).inflate(1.0));
        }
        working = out;
        hidden = boxes;
    }

    /** Is this point inside an invisible zone (so nothing is drawn there)? */
    public static boolean hiddenAt(double x, double y, double z) {
        for (net.minecraft.world.phys.AABB box : hidden) {
            if (box.contains(x, y, z)) return true;
        }
        return false;
    }

    public static boolean hiddenAt(net.minecraft.world.phys.Vec3 p) {
        return !hidden.isEmpty() && hiddenAt(p.x, p.y, p.z);
    }

    /** {@code level.addParticle}, unless it would appear inside an invisible zone. */
    public static void particle(net.minecraft.world.level.Level level, net.minecraft.core.particles.ParticleOptions type,
                                double x, double y, double z, double dx, double dy, double dz) {
        if (!hidden.isEmpty() && hiddenAt(x, y, z)) return;
        level.addParticle(type, x, y, z, dx, dy, dz);
    }

    private ClientAnomalyCache() {
    }
}
