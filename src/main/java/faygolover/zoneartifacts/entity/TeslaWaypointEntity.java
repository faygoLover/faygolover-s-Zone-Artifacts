package faygolover.zoneartifacts.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A small, precisely-clickable marker standing in for one Tesla route waypoint — the exact target
 * of every "click this existing point" interaction (see {@code TeslaRouteInteractionHandler}), so
 * finding one is never a matter of raytracing, inflated hitboxes, or guessing: it's whichever entity
 * Minecraft's own entity-interaction resolution says was clicked, full stop. One of these exists for
 * every point of every in-progress chain and every finished route alike, spawned the moment a point
 * is added and discarded the moment it's removed (see {@code TeslaWaypointMarkers}); what a given
 * marker's position <em>currently means</em> (part of which chain, or which finished route) is looked
 * up fresh at click time from {@link TeslaSavedData} / the route tool's own NBT, never stored here.
 * <p>
 * Deliberately smaller than a full block ({@link #SIZE}) rather than bigger: a Tesla spawns exactly
 * where her route's markers are, so keeping them small keeps her clear of solid terrain and visible
 * the instant her route closes, rather than embedded in whatever block was clicked to place the
 * point (see {@code TeslaRouteInteractionHandler}'s face-offset placement).
 */
public class TeslaWaypointEntity extends Entity {

    private static final float SIZE = 0.3f;

    private BlockPos pos = BlockPos.ZERO;

    public TeslaWaypointEntity(EntityType<? extends TeslaWaypointEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    /** Positions this marker at the exact center of {@code pos} and remembers it as the waypoint
     *  this entity stands for - call once, right after spawning. */
    public void setWaypointPos(BlockPos pos) {
        this.pos = pos.immutable();
        this.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public BlockPos waypointPos() {
        return pos;
    }

    /** A pure position marker: never moves, never ages out, no physics of its own - skipping
     *  {@code super.tick()} entirely means no gravity/fluid-push/fire-tick bookkeeping can ever
     *  nudge her off the exact spot a GM placed her at. */
    @Override
    public void tick() {
        // Intentionally empty - see class javadoc.
    }

    @Override
    protected void defineSynchedData() {
        // No synced state - the client only needs her position (ordinary entity tracking) and
        // TeslaRouteHighlightRenderer draws the actual visual from that alone.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.pos = BlockPos.of(tag.getLong("Pos"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("Pos", pos.asLong());
    }

    /** A valid interact/attack target — the whole point of this entity - unlike {@link
     *  TeslaEntity#isPickable}, which is deliberately the opposite. */
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    public static float size() {
        return SIZE;
    }
}
