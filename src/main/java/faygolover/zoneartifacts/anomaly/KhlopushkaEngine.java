package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.KhlopushkaPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Khlopushka: a quarter of a second after someone (a player or a mob) walks into the zone, and then
 * {@code cooldown} seconds after each blast while anyone is still in it, a glowing clot appears in
 * the open before a random one of them, gathers itself ({@code chargeSeconds} / the speed tuner) and
 * blasts — heat for everyone within {@code blastRadius} (full up close, a third at the edge), and a
 * flash that blinds whoever was looking at it from closer than the targeting tuner.
 */
public final class KhlopushkaEngine {

    public static final ResourceLocation CHARGE_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "khlopushka_charge");
    public static final ResourceLocation BANG_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "khlopushka_bang");
    private static final double LOOK_COS = Math.cos(Math.toRadians(70.0));
    /** The first clot after someone walks in, ticks. */
    private static final int FIRST_DELAY = 5;

    private static final class Clot {
        Vec3 at;
        long start;
        int charge;
    }

    private static final Map<AnomalyInstance, Clot> CLOTS = new WeakHashMap<>();
    private static final Map<AnomalyInstance, Long> NEXT = new WeakHashMap<>();
    /** Zones with someone in them (as of the last tick). */
    private static final Map<AnomalyInstance, Boolean> OCCUPIED = new WeakHashMap<>();

    private KhlopushkaEngine() {
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        long now = level.getGameTime();
        Clot clot = CLOTS.get(instance);
        if (clot != null) {
            if (now - clot.start >= clot.charge) {
                CLOTS.remove(instance);
                blast(level, instance, clot.at);
                NEXT.put(instance, now + AnomalyDefaults.ticks(instance.cooldownSeconds()));
            }
            return;
        }
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, zone, KhlopushkaEngine::target);
        if (inside.isEmpty()) {
            OCCUPIED.remove(instance);
            return;
        }
        if (OCCUPIED.put(instance, Boolean.TRUE) == null) {
            // Someone just walked in: the first one comes almost at once (unless it's still resting after a blast).
            NEXT.merge(instance, now + FIRST_DELAY, Math::max);
        }
        long next = NEXT.getOrDefault(instance, now);
        if (now < next) return;
        LivingEntity victim = inside.get(level.random.nextInt(inside.size()));
        // Right in front of its eyes, at their height (looking up or down doesn't lift it off that level).
        Vec3 eye = victim.getEyePosition();
        Vec3 look = victim.getLookAngle();
        look = new Vec3(look.x, 0.0, look.z);
        look = look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0f, victim.getYRot()) : look.normalize();
        double want = 3.0 + level.random.nextDouble() * 1.5;
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(want)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(want)) : hit.getLocation().subtract(look.scale(0.6));
        if (at.distanceTo(eye) < 1.2) at = eye.add(look.scale(1.2));
        clot = new Clot();
        clot.at = at;
        clot.start = now;
        clot.charge = Math.max(4, AnomalyDefaults.ticks(ModCommonConfig.KHLOP_CHARGE_SECONDS.get() / Math.max(0.1, instance.speed())));
        CLOTS.put(instance, clot);
        NEXT.put(instance, Long.MAX_VALUE);
        KhlopushkaPacket.spawn(level, at, clot.charge);
        // The charge sound is 1 s long: a quicker charge plays it higher, a slower one lower.
        AnomalyCombat.playSound(level, at, CHARGE_SOUND, 1.0f, Math.max(0.5f, Math.min(2.0f, 20.0f / clot.charge)));
    }

    private static boolean target(LivingEntity e) {
        if (!e.isAlive() || AnomalyCombat.spectatorExempt(e)) return false;
        return !AnomalyCombat.creativeExempt(e);
    }

    private static void blast(ServerLevel level, AnomalyInstance instance, Vec3 at) {
        double r = ModCommonConfig.KHLOP_RADIUS.get();
        AnomalyCombat.playSound(level, at, BANG_SOUND, 3.0f, 0.95f + level.random.nextFloat() * 0.1f);
        KhlopushkaPacket.blast(level, at, (float) r);
        float damage = instance.damage();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), x -> x.isAlive() && !AnomalyCombat.spectatorExempt(x))) {
            if (AnomalyCombat.creativeExempt(e)) continue;
            double d = e.getBoundingBox().getCenter().distanceTo(at);
            if (d > r) continue;
            e.invulnerableTime = 0;
            AnomalyCombat.hurt(level, e, Thermal.HEAT_DAMAGE_TYPE, (float) (damage * (1.0 - (2.0 / 3.0) * d / r)), at);
        }
        // The flash: only for those looking at it, close enough, with nothing in between.
        double range = instance.range() > 0 ? instance.range() : ModCommonConfig.KHLOP_RANGE.get();
        float seconds = (float) (ModCommonConfig.KHLOP_BLIND_SECONDS.get() * Math.max(1, instance.intensity()) / 3.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(range), x -> x.isAlive() && !AnomalyCombat.spectatorExempt(x))) {
            Vec3 eye = e.getEyePosition();
            Vec3 to = at.subtract(eye);
            double d = to.length();
            if (d > range || d < 1.0E-3) continue;
            if (e.getLookAngle().dot(to.scale(1.0 / d)) < LOOK_COS) continue;
            BlockHitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(eye) < d * d - 0.25) continue;
            // Fully blind — but over by the time the next one can come (the pause after a blast).
            float strength = 1.0f;
            int ticks = Math.max(10, Math.min((int) (seconds * 20.0f), AnomalyDefaults.ticks(instance.cooldownSeconds()) - 4));
            if (e instanceof ServerPlayer player) {
                KhlopushkaPacket.flash(player, strength, ticks);
            } else if (e instanceof Mob mob) {
                // Blinded, a mob loses whoever it was after and stumbles about.
                mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 2));
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }
    }
}
