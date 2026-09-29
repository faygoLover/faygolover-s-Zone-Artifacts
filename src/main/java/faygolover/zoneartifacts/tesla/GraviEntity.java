package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.anomaly.AnomalyCombat;
import faygolover.zoneartifacts.anomaly.Gravity;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.GraviPopPacket;
import faygolover.zoneartifacts.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

        // Pops on the surfaces around it: more with a bigger reach.
        double perSecond = Math.min(Gravi.MAX_POPS_PER_SECOND, ModCommonConfig.GRAVI_POPS_PER_SECOND.get() * size * size);
        surfaceBudget += perSecond / 20.0;
        while (surfaceBudget >= 1.0) {
            surfaceBudget -= 1.0;
            surfacePop(level, c, size, now);
        }
        // And right by itself, in the air too — more often while it sits in its prey.
        double selfSeconds = ModCommonConfig.GRAVI_SELF_POP_SECONDS.get();
        if (selfSeconds > 0.0) {
            int interval = Math.max(2, (int) Math.round(selfSeconds * 20.0 / (getState() == State.CHASE ? 1.5 : 1.0)));
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

    private void surfacePop(ServerLevel level, Vec3 c, double size, long now) {
        for (int attempt = 0; attempt < 3; attempt++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSqr() < 1.0E-6) continue;
            dir = dir.normalize();
            BlockHitResult hit = Razlom.clipBlocks(level, c, c.add(dir.scale(size)));
            if (hit.getType() == HitResult.Type.MISS) continue;
            Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            schedule(level, hit.getLocation().add(normal.scale(0.3)), normal, level.getBlockState(hit.getBlockPos()), now);
            return;
        }
    }

    private void schedule(ServerLevel level, Vec3 pos, Vec3 normal, BlockState surface, long now) {
        pending.add(new Pop(pos, now + Gravi.WINDUP_TICKS));
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, 64.0, level.dimension())),
                new GraviPopPacket(pos, normal, surface == null ? 0 : Block.getId(surface)));
    }

    private void burst(ServerLevel level, Vec3 pos, long now) {
        AnomalyCombat.playSound(level, pos, Gravi.POP_SOUND, 1.0f, 0.9f + random.nextFloat() * 0.2f);
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
