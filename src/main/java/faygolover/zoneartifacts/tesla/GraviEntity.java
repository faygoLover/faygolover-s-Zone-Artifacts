package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.anomaly.AnomalyCombat;
import faygolover.zoneartifacts.anomaly.Gravity;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.GraviPopPacket;
import faygolover.zoneartifacts.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gravi (see {@link Gravi}): an invisible route anomaly that passes through everything and leaves a
 * chain of small gravitational pops behind it. Each pop sucks in for {@link Gravi#WINDUP_TICKS}
 * ticks, then bursts: whoever is within {@link Gravi#POP_RADIUS} blocks is hurt (at most once per
 * {@code gravi.hitIntervalTicks}) and shoved away. The pops' look is the client's
 * ({@code client.gravi.GraviClient}).
 */
public class GraviEntity extends TeslaEntity {

    private record Pop(Vec3 pos, long due) {
    }

    private final List<Pop> pending = new ArrayList<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private double surfaceBudget;
    private int selfPopTimer;
    /** Where it has been heading lately (the footprints go this way). */
    private Vec3 heading = new Vec3(1, 0, 0);
    @javax.annotation.Nullable
    private Vec3 lastCenter;
    private int stepSide = 1;
    /** How far it went over roughly the last second (standing still, it barely steps). */
    private double recentTravel = 1.0;

    public GraviEntity(EntityType<? extends TeslaEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Always a quarter of a block, whatever its size (the size is its reach). */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(Gravi.HITBOX, Gravi.HITBOX);
    }

    /** Touching someone does nothing by itself — only the pops hurt. */
    @Override
    protected void shock(ServerLevel level, TeslaRoute route, List<LivingEntity> touched) {
    }

    /** Never flies into anything (it passes through), so never pops. */
    @Override
    protected void onBlockCollision(ServerLevel level, TeslaRoute route, Vec3 normal) {
    }

    /** Straight at the target, but never further than the leash from its nearest route point. */
    @Override
    protected Vec3 chaseDestination(TeslaRoute route, Player target) {
        Vec3 want = target.getBoundingBox().getCenter();
        double leash = ModCommonConfig.GRAVI_LEASH.get();
        Vec3 anchor = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : route.waypoints()) {
            Vec3 c = TeslaGeometry.center(p);
            double d = c.distanceToSqr(want);
            if (d < best) {
                best = d;
                anchor = c;
            }
        }
        if (anchor == null || best <= leash * leash) return want;
        return anchor.add(want.subtract(anchor).normalize().scale(leash));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && getState().isVisible()) popTick(level);
    }

    private void popTick(ServerLevel level) {
        long now = level.getGameTime();
        Vec3 c = center();
        double size = Math.max(1.0, getSize());
        if (lastCenter != null) {
            Vec3 motion = c.subtract(lastCenter);
            double moved = motion.length();
            if (moved > 1.0E-4 && moved < 4.0) heading = heading.scale(0.8).add(motion.scale(0.2 / moved)).normalize();
            recentTravel = recentTravel * 0.95 + Math.min(moved, 1.0);
        }
        lastCenter = c;

        // Pops on the surfaces around it (the size is only how far): as many as the effects tuner says.
        double perSecond = Math.min(Gravi.MAX_POPS_PER_SECOND,
                ModCommonConfig.GRAVI_POPS_PER_SECOND.get() * Math.max(1, getIntensity()) / 3.0);
        // Standing still, it only shifts from foot to foot now and then.
        double moving = Mth.clamp(recentTravel / 1.0, 0.25, 1.0);
        surfaceBudget += perSecond * moving / 20.0;
        while (surfaceBudget >= 1.0) {
            surfaceBudget -= 1.0;
            surfacePop(level, c, size, now);
        }
        // And right by itself, in the air too — only while it sits in its prey.
        double selfSeconds = ModCommonConfig.GRAVI_SELF_POP_SECONDS.get();
        if (selfSeconds > 0.0 && getState() == State.CHASE && nearPrey(level)) {
            int interval = Math.max(2, (int) Math.round(selfSeconds * 20.0));
            if (++selfPopTimer >= interval) {
                selfPopTimer = 0;
                Vec3 p = c.add(random.nextGaussian() * 0.35, random.nextGaussian() * 0.35, random.nextGaussian() * 0.35);
                schedule(level, p, null, null, now);
            }
        }

        for (Iterator<Pop> it = pending.iterator(); it.hasNext(); ) {
            Pop pop = it.next();
            if (now < pop.due()) continue;
            it.remove();
            burst(level, pop.pos(), now);
        }
    }

    /** Its prey is right here (it hangs inside it). */
    private boolean nearPrey(ServerLevel level) {
        for (Player player : level.players()) {
            if (isChasing(player) && player.getBoundingBox().inflate(1.0).contains(center())) return true;
        }
        return false;
    }

    /**
     * A pop on a real surface near it, laid like a footprint: a little ahead of where it is going,
     * to the left and to the right in turn, with some scatter — so a chain of them reads as steps
     * coming closer. Mostly on the floor under it, but now and then (or when there is no floor within
     * reach) on a wall or the ceiling beside the step instead. Never in mid-air or inside a block.
     */
    private void surfacePop(ServerLevel level, Vec3 c, double size, long now) {
        Vec3 side = heading.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        stepSide = -stepSide;
        double scatter = 0.12 + 0.08 * size;
        Vec3 flat = new Vec3(heading.x, 0.0, heading.z);
        Vec3 ahead = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(0.35);
        Vec3 from = c.add(ahead).add(side.scale(stepSide * 0.3))
                .add(random.nextGaussian() * scatter, random.nextGaussian() * scatter * 0.5, random.nextGaussian() * scatter);
        if (!level.getBlockState(BlockPos.containing(from)).getCollisionShape(level, BlockPos.containing(from)).isEmpty()) {
            from = c;
            if (!level.getBlockState(BlockPos.containing(c)).getCollisionShape(level, BlockPos.containing(c)).isEmpty()) return;
        }
        double reach = 1.5 + size;
        BlockHitResult floor = Razlom.clipBlocks(level, from, from.add(0.0, -reach, 0.0));
        BlockHitResult other = null;
        double otherDist = Double.MAX_VALUE;
        for (Vec3 dir : new Vec3[]{new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(0, 1, 0)}) {
            BlockHitResult hit = Razlom.clipBlocks(level, from, from.add(dir.scale(reach)));
            if (hit.getType() == HitResult.Type.MISS) continue;
            double d = hit.getLocation().distanceToSqr(from);
            if (d < otherDist) {
                otherDist = d;
                other = hit;
            }
        }
        boolean hasFloor = floor.getType() != HitResult.Type.MISS;
        BlockHitResult hit;
        // A wall or ceiling only when it is right beside the step (so the trail doesn't jump about).
        boolean closeOther = other != null && otherDist <= 1.3 * 1.3;
        if (hasFloor && (!closeOther || random.nextFloat() >= Gravi.OFF_FLOOR_CHANCE)) hit = floor;
        else if (other != null) hit = other;
        else return;
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 at = hit.getLocation().add(normal.scale(0.3));
        BlockPos atPos = BlockPos.containing(at);
        if (!level.getBlockState(atPos).getCollisionShape(level, atPos).isEmpty()) return;
        schedule(level, at, normal, level.getBlockState(hit.getBlockPos()), now);
    }

    private void schedule(ServerLevel level, Vec3 pos, Vec3 normal, BlockState surface, long now) {
        pending.add(new Pop(pos, now + Gravi.WINDUP_TICKS));
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, 64.0, level.dimension())),
                new GraviPopPacket(pos, normal, surface == null ? 0 : Block.getId(surface)));
    }

    private void burst(ServerLevel level, Vec3 pos, long now) {
        AnomalyCombat.playSound(level, pos, Gravi.POP_SOUND, Gravi.POP_VOLUME, 0.9f + random.nextFloat() * 0.2f);
        TeslaRoute route = TeslaRouteSavedData.get(level).get(routeId());
        float damage = route != null ? route.damage() : ModCommonConfig.GRAVI_DAMAGE.get().floatValue();
        int interval = ModCommonConfig.GRAVI_HIT_INTERVAL_TICKS.get();
        lastHit.values().removeIf(t -> now - t > 200);
        AABB box = new AABB(pos, pos).inflate(Gravi.POP_RADIUS + 1.0);
        for (Entity e : level.getEntities(this, box, Gravity::movable)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(pos);
            double d = to.length();
            if (d > Gravi.POP_RADIUS) continue;
            Vec3 dir = d < 1.0E-3 ? new Vec3(0, 1, 0) : to.scale(1.0 / d);
            double k = Gravi.POP_PUSH * (0.4 + 0.6 * (1.0 - d / Gravi.POP_RADIUS));
            e.setDeltaMovement(e.getDeltaMovement().add(dir.x * k, dir.y * k * 0.5 + 0.12, dir.z * k));
            e.hurtMarked = true;
            e.hasImpulse = true;
            if (e instanceof LivingEntity living && AnomalyCombat.isValidTeslaTarget(living) && damage > 0.0f) {
                Long last = lastHit.get(living.getUUID());
                if (last != null && now - last < interval) continue;
                lastHit.put(living.getUUID(), now);
                living.invulnerableTime = 0;
                AnomalyCombat.hurt(level, living, Gravi.DAMAGE_TYPE, damage, pos);
            }
        }
    }
}
