package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player, in-progress Tesla route builds — deliberately kept in memory only, never persisted:
 * per the spec, logging out, changing dimension, or the server shutting down all discard an
 * unfinished build outright rather than resuming it later. A completed route becomes a {@link
 * TeslaRouteSavedData} entry instead, which *is* persisted.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class TeslaBuildManager {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private TeslaBuildManager() {
    }

    public static Session getOrNull(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public static Session getOrCreate(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session(player.level().dimension().location()));
    }

    public static void discard(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /** Every currently in-progress build session in the given dimension, keyed by builder UUID. */
    public static Map<UUID, Session> allSessionsIn(net.minecraft.resources.ResourceLocation dimension) {
        Map<UUID, Session> result = new HashMap<>();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            if (entry.getValue().dimension().equals(dimension)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** @return true if any build session is currently in progress anywhere (used by the admin cleanup command). */
    public static boolean hasAnySession() {
        return !SESSIONS.isEmpty();
    }

    public static void discardAll() {
        SESSIONS.clear();
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            discard(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            discard(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
    }

    /** One player's in-progress route: an ordered list of not-yet-finalized waypoint positions. */
    public static final class Session {
        private final net.minecraft.resources.ResourceLocation dimension;
        private final List<BlockPos> points = new ArrayList<>();

        private Session(net.minecraft.resources.ResourceLocation dimension) {
            this.dimension = dimension;
        }

        public net.minecraft.resources.ResourceLocation dimension() {
            return dimension;
        }

        public List<BlockPos> points() {
            return points;
        }

        public BlockPos startPoint() {
            return points.isEmpty() ? null : points.get(0);
        }

        public void addPoint(BlockPos pos) {
            points.add(pos);
        }

        /** @return true if the point was found and removed. */
        public boolean removePoint(BlockPos pos) {
            return points.remove(pos);
        }

        public int indexOf(BlockPos pos) {
            return points.indexOf(pos);
        }
    }
}
