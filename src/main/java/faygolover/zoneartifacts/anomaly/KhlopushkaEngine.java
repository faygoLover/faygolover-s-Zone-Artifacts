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
 * Khlopushka: every {@code cooldown} seconds, if a player is in the zone, a glowing clot appears in
 * the open before a random one of them, gathers itself ({@code chargeSeconds} / the speed tuner) and
 * blasts — heat for everyone within {@code blastRadius} (full up close, a third at the edge), and a
 * flash that blinds whoever was looking at it from closer than the targeting tuner.
 */
public final class KhlopushkaEngine {

    public static final ResourceLocation CHARGE_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "khlopushka_charge");
    public static final ResourceLocation BANG_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "khlopushka_bang");
    private static final double LOOK_COS = Math.cos(Math.toRadians(55.0));

    private static final class Clot {
        Vec3 at;
        long start;
        int charge;
    }

    private static final Map<AnomalyInstance, Clot> CLOTS = new WeakHashMap<>();
    private static final Map<AnomalyInstance, Long> NEXT = new WeakHashMap<>();

    private KhlopushkaEngine() {
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        long now = level.getGameTime();
        Clot clot = CLOTS.get(instance);
        if (clot != null) {
            if (now - clot.start >= clot.charge) {
                CLOTS.remove(instance);
                blast(level, instance, clot.at);
            }
            return;
        }
        long next = NEXT.computeIfAbsent(instance, k -> now + AnomalyDefaults.ticks(instance.cooldownSeconds()));
        if (now < next || now % 5 != 0) return;
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        List<Player> inside = level.getEntitiesOfClass(Player.class, zone, p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        if (inside.isEmpty()) return;
        Player victim = inside.get(level.random.nextInt(inside.size()));
        Vec3 eye = victim.getEyePosition();
        Vec3 look = victim.getLookAngle();
        double want = 3.0 + level.random.nextDouble() * 1.5;
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(want)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, victim));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(want)) : hit.getLocation().subtract(look.scale(0.6));
        if (at.distanceTo(eye) < 1.2) at = eye.add(look.scale(1.2));
        clot = new Clot();
        clot.at = at;
        clot.start = now;
        clot.charge = Math.max(4, AnomalyDefaults.ticks(ModCommonConfig.KHLOP_CHARGE_SECONDS.get() / Math.max(0.1, instance.speed())));
        CLOTS.put(instance, clot);
        NEXT.put(instance, now + clot.charge + AnomalyDefaults.ticks(instance.cooldownSeconds()));
        KhlopushkaPacket.spawn(level, at, clot.charge);
        // The charge sound is 2 s long: a quicker charge plays it higher.
        AnomalyCombat.playSound(level, at, CHARGE_SOUND, 1.0f, Math.max(0.5f, Math.min(2.0f, 40.0f / clot.charge)));
    }

    private static void blast(ServerLevel level, AnomalyInstance instance, Vec3 at) {
        double r = ModCommonConfig.KHLOP_RADIUS.get();
        AnomalyCombat.playSound(level, at, BANG_SOUND, 3.0f, 0.95f + level.random.nextFloat() * 0.1f);
        KhlopushkaPacket.blast(level, at, (float) r);
        float damage = instance.damage();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), x -> x.isAlive() && !x.isSpectator())) {
            if (e instanceof Player p && p.isCreative()) continue;
            double d = e.getBoundingBox().getCenter().distanceTo(at);
            if (d > r) continue;
            e.invulnerableTime = 0;
            AnomalyCombat.hurt(level, e, Thermal.HEAT_DAMAGE_TYPE, (float) (damage * (1.0 - (2.0 / 3.0) * d / r)), at);
        }
        // The flash: only for those looking at it, close enough, with nothing in between.
        double range = instance.range() > 0 ? instance.range() : ModCommonConfig.KHLOP_RANGE.get();
        float seconds = (float) (ModCommonConfig.KHLOP_BLIND_SECONDS.get() * Math.max(1, instance.intensity()) / 3.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(range), x -> x.isAlive() && !x.isSpectator())) {
            Vec3 eye = e.getEyePosition();
            Vec3 to = at.subtract(eye);
            double d = to.length();
            if (d > range || d < 1.0E-3) continue;
            if (e.getLookAngle().dot(to.scale(1.0 / d)) < LOOK_COS) continue;
            BlockHitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(eye) < d * d - 0.25) continue;
            float strength = (float) (1.0 - 0.6 * d / range);
            int ticks = Math.max(10, (int) (seconds * 20.0f * strength));
            if (e instanceof ServerPlayer player) {
                KhlopushkaPacket.flash(player, strength, ticks);
            } else if (e instanceof Mob) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
            }
        }
    }
}
