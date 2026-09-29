package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-dimension persistence for Tesla routes — {@link AnomalySavedData}'s counterpart for the
 * route-following family. Unlike a placed Electra, a Tesla's own live {@link TeslaEntity} is a
 * perfectly ordinary entity that saves/loads with its chunk automatically; what needs its own
 * bookkeeping here is everything that has to survive the entity NOT existing for a few seconds:
 * the route itself (so a respawn knows where to go) and the "waiting to respawn" countdown.
 * <p>
 * {@code pendingSecondHits} — Tesla's second, delayed damage tick at the end of the electrify
 * window (see {@code TeslaEntity#strike}) — is deliberately <em>not</em> persisted: it only ever
 * spans up to a type's {@code electrifyTicks} (a second or so), so losing it across a save/reload
 * in that tiny window is an acceptable trade for not needing to serialize a UUID+countdown list
 * that's normally empty anyway.
 */
public class TeslaSavedData extends SavedData {

    private static final String ID = "fl_zone_arts_tesla_routes";

    private final List<TeslaRoute> routes = new ArrayList<>();
    private final Map<Integer, Integer> pendingRespawnTicks = new HashMap<>();
    private final List<PendingSecondHit> pendingSecondHits = new ArrayList<>();
    private int nextRouteId = 1;

    public static TeslaSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TeslaSavedData::load, TeslaSavedData::new, ID);
    }

    public List<TeslaRoute> routes() {
        return List.copyOf(routes);
    }

    @Nullable
    public TeslaRoute routeById(int id) {
        for (TeslaRoute route : routes) {
            if (route.id() == id) return route;
        }
        return null;
    }

    @Nullable
    public TeslaRoute routeContaining(BlockPos pos) {
        for (TeslaRoute route : routes) {
            if (route.contains(pos)) return route;
        }
        return null;
    }

    /** Creates and persists a new closed-loop route, and spawns its first Tesla immediately at
     *  waypoint 0 (subsequent respawns pick a random waypoint — only the very first spawn is
     *  deterministic, so a GM sees the entity appear right where they just finished the loop). */
    public TeslaRoute createRoute(ServerLevel level, ResourceLocation typeId, List<BlockPos> waypoints) {
        TeslaRoute route = new TeslaRoute(nextRouteId++, typeId, waypoints);
        routes.add(route);
        setDirty();
        spawnAt(level, route, 0);
        return route;
    }

    /** Removes a route entirely: cancels any pending respawn and despawns its live Tesla, if any
     *  (found by route id, not by remembering a specific entity — see {@link TeslaEntity#routeId()}). */
    public void removeRoute(ServerLevel level, int routeId) {
        routes.removeIf(r -> r.id() == routeId);
        pendingRespawnTicks.remove(routeId);
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof TeslaEntity tesla && tesla.routeId() == routeId) {
                tesla.discard();
            }
        }
        setDirty();
    }

    public void scheduleRespawn(int routeId, int ticks) {
        pendingRespawnTicks.put(routeId, Math.max(1, ticks));
        setDirty();
    }

    public void schedulePendingHit(UUID targetUuid, ResourceLocation damageType, float damage, int ticks) {
        pendingSecondHits.add(new PendingSecondHit(targetUuid, damageType, damage, Math.max(1, ticks)));
    }

    /** Advances every pending respawn and pending second-hit by one tick, spawning/applying
     *  whatever just reached zero. Called once per level tick from {@code TeslaEngine}. */
    public void tick(ServerLevel level) {
        tickRespawns(level);
        tickPendingHits(level);
    }

    private void tickRespawns(ServerLevel level) {
        if (pendingRespawnTicks.isEmpty()) return;
        boolean changed = false;
        Iterator<Map.Entry<Integer, Integer>> it = pendingRespawnTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                TeslaRoute route = routeById(entry.getKey());
                if (route != null && !route.waypoints().isEmpty()) {
                    int waypointIndex = level.random.nextInt(route.waypoints().size());
                    spawnAt(level, route, waypointIndex);
                }
                it.remove();
                changed = true;
            } else {
                entry.setValue(remaining);
            }
        }
        if (changed) setDirty();
    }

    private void tickPendingHits(ServerLevel level) {
        if (pendingSecondHits.isEmpty()) return;
        Iterator<PendingSecondHit> it = pendingSecondHits.iterator();
        while (it.hasNext()) {
            PendingSecondHit hit = it.next();
            hit.ticksRemaining--;
            if (hit.ticksRemaining > 0) continue;
            it.remove();
            hit.apply(level);
        }
    }

    private void spawnAt(ServerLevel level, TeslaRoute route, int waypointIndex) {
        TeslaEntity entity = new TeslaEntity(ModEntities.TESLA.get(), level);
        BlockPos pos = route.waypoints().get(waypointIndex);
        entity.moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0f, 0.0f);
        entity.initRoute(route.id(), route.typeId(), waypointIndex);
        level.addFreshEntity(entity);
    }

    // ---- NBT ----------------------------------------------------------

    public static TeslaSavedData load(CompoundTag tag) {
        TeslaSavedData data = new TeslaSavedData();
        data.nextRouteId = tag.getInt("nextRouteId");
        if (data.nextRouteId < 1) data.nextRouteId = 1;

        ListTag routesTag = tag.getList("routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < routesTag.size(); i++) {
            CompoundTag routeTag = routesTag.getCompound(i);
            int id = routeTag.getInt("id");
            ResourceLocation typeId = new ResourceLocation(routeTag.getString("type"));
            ListTag waypointsTag = routeTag.getList("waypoints", Tag.TAG_LONG);
            List<BlockPos> waypoints = new ArrayList<>(waypointsTag.size());
            for (int j = 0; j < waypointsTag.size(); j++) {
                // ListTag has no getLong(int) (unlike CompoundTag) - pull the raw LongTag element
                // out and read it directly instead.
                long packed = ((LongTag) waypointsTag.get(j)).getAsLong();
                waypoints.add(BlockPos.of(packed));
            }
            data.routes.add(new TeslaRoute(id, typeId, waypoints));
        }

        ListTag pendingTag = tag.getList("pendingRespawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingTag.size(); i++) {
            CompoundTag entry = pendingTag.getCompound(i);
            data.pendingRespawnTicks.put(entry.getInt("routeId"), entry.getInt("ticks"));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextRouteId", nextRouteId);

        ListTag routesTag = new ListTag();
        for (TeslaRoute route : routes) {
            CompoundTag routeTag = new CompoundTag();
            routeTag.putInt("id", route.id());
            routeTag.putString("type", route.typeId().toString());
            ListTag waypointsTag = new ListTag();
            for (BlockPos pos : route.waypoints()) {
                waypointsTag.add(LongTag.valueOf(pos.asLong()));
            }
            routeTag.put("waypoints", waypointsTag);
            routesTag.add(routeTag);
        }
        tag.put("routes", routesTag);

        ListTag pendingTag = new ListTag();
        for (Map.Entry<Integer, Integer> entry : pendingRespawnTicks.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("routeId", entry.getKey());
            entryTag.putInt("ticks", entry.getValue());
            pendingTag.add(entryTag);
        }
        tag.put("pendingRespawns", pendingTag);

        return tag;
    }

    private static final class PendingSecondHit {
        final UUID targetUuid;
        final ResourceLocation damageType;
        final float damage;
        int ticksRemaining;

        PendingSecondHit(UUID targetUuid, ResourceLocation damageType, float damage, int ticksRemaining) {
            this.targetUuid = targetUuid;
            this.damageType = damageType;
            this.damage = damage;
            this.ticksRemaining = ticksRemaining;
        }

        void apply(ServerLevel level) {
            Entity entity = level.getEntity(targetUuid);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                TeslaEntity.dealDamage(level, living, damageType, damage);
            }
        }
    }
}
