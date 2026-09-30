package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.block.PukhBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Burning Fluff firing together: whichever strand notices something coming fast, the whole growth
 * answers — but only the strands that can hit it (in range, nothing in between), one after another:
 * the nearest first, then every {@link #GAP} ticks the next, each aiming at where the target is by
 * then, and holding fire if it's out of range or out of sight by then.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class PukhVolley {

    public static final int GAP = 2;
    /** Strands farther than this from the target never join in. */
    private static final double GATHER = 24.0;

    private static final class Volley {
        final ServerLevel level;
        final Entity target;
        final Deque<PukhBlockEntity> queue;
        long next;

        Volley(ServerLevel level, Entity target, Deque<PukhBlockEntity> queue, long next) {
            this.level = level;
            this.target = target;
            this.queue = queue;
            this.next = next;
        }
    }

    private static final List<Volley> VOLLEYS = new ArrayList<>();

    private PukhVolley() {
    }

    /** Something fast came near: every strand that can hit it fires in turn (unless a volley at it is on already). */
    public static void start(ServerLevel level, Entity target) {
        for (Volley v : VOLLEYS) {
            if (v.target == target) return;
        }
        Vec3 at = target.getBoundingBox().getCenter();
        List<PukhBlockEntity> able = new ArrayList<>();
        for (PukhBlockEntity be : List.copyOf(PukhBlockEntity.SERVER_LOADED)) {
            if (be.isRemoved() || be.getLevel() != level || !be.ready()) continue;
            Vec3 origin = be.puffOrigin();
            if (origin.distanceTo(at) > GATHER || !canHit(level, be, origin, at)) continue;
            able.add(be);
        }
        if (able.isEmpty()) return;
        able.sort(Comparator.comparingDouble(be -> be.puffOrigin().distanceToSqr(at)));
        VOLLEYS.add(new Volley(level, target, new ArrayDeque<>(able), level.getGameTime()));
    }

    private static boolean canHit(ServerLevel level, PukhBlockEntity be, Vec3 origin, Vec3 at) {
        if (be.range() <= 0.0 || origin.distanceTo(at) > be.range()) return false;
        BlockHitResult hit = level.clip(new ClipContext(origin, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(origin) >= origin.distanceToSqr(at) - 0.01;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || VOLLEYS.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Volley> it = VOLLEYS.iterator(); it.hasNext(); ) {
            Volley v = it.next();
            if (v.level != level) continue;
            if (!v.target.isAlive() || v.target.level() != level) {
                it.remove();
                continue;
            }
            if (now < v.next) continue;
            // The next that still can: those that can't any more (out of range or of sight) hold fire.
            Vec3 at = v.target.getBoundingBox().getCenter();
            while (!v.queue.isEmpty()) {
                PukhBlockEntity be = v.queue.poll();
                if (be.isRemoved() || !be.ready() || !canHit(level, be, be.puffOrigin(), at)) continue;
                be.fire(level, at);
                break;
            }
            v.next = now + GAP;
            if (v.queue.isEmpty()) it.remove();
        }
    }
}
