package faygolover.zoneartifacts.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Routes that are still being built, one per GM at most. Memory only, on purpose: an unfinished
 * route is dropped when its builder logs out, changes dimension, or the server stops (see
 * {@code TeslaSyncHandler}), so nothing half-built can ever end up in the save.
 * <p>
 * Drafts get negative ids ({@code -1, -2, ...}) so a single int identifies either a draft or a
 * completed route ({@link TeslaRouteSavedData}, positive ids) in packets and raytraces.
 */
public final class TeslaDrafts {

    public static final class Draft {
        private final int id;
        private final UUID owner;
        private final String ownerName;
        private final ResourceKey<Level> dimension;
        private final List<BlockPos> points = new ArrayList<>();

        Draft(int id, UUID owner, String ownerName, ResourceKey<Level> dimension, BlockPos start) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.dimension = dimension;
            this.points.add(start.immutable());
        }

        public int id() {
            return id;
        }

        public UUID owner() {
            return owner;
        }

        public String ownerName() {
            return ownerName;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        /** Live list — only {@link TeslaRouteService} mutates it. */
        public List<BlockPos> points() {
            return points;
        }

        public BlockPos start() {
            return points.get(0);
        }
    }

    private static final Map<UUID, Draft> BY_OWNER = new HashMap<>();
    private static int nextId = -1;

    private TeslaDrafts() {
    }

    @Nullable
    public static Draft of(UUID owner) {
        return BY_OWNER.get(owner);
    }

    public static Draft start(UUID owner, String ownerName, ResourceKey<Level> dimension, BlockPos start) {
        Draft draft = new Draft(nextId--, owner, ownerName, dimension, start);
        BY_OWNER.put(owner, draft);
        return draft;
    }

    @Nullable
    public static Draft byId(int id) {
        for (Draft draft : BY_OWNER.values()) {
            if (draft.id == id) return draft;
        }
        return null;
    }

    @Nullable
    public static Draft remove(UUID owner) {
        return BY_OWNER.remove(owner);
    }

    public static Collection<Draft> all() {
        return BY_OWNER.values();
    }

    public static List<Draft> inDimension(ResourceKey<Level> dimension) {
        List<Draft> result = new ArrayList<>();
        for (Draft draft : BY_OWNER.values()) {
            if (draft.dimension.equals(dimension)) result.add(draft);
        }
        return result;
    }

    public static void clear() {
        BY_OWNER.clear();
    }
}
