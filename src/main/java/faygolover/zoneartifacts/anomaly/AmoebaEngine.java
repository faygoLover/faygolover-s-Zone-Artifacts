package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.AmoebaEventPacket;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server side of the Amoeba ({@link Amoeba}): the trigger, the lashes and their hits, the
 * cooldown. The client is told when it gathers, of every lash (where it aims) and when it settles.
 */
public final class AmoebaEngine {

    private record Lash(Vec3 origin, Vec3 tip, long hitTick) {
    }

    private static final Map<AnomalyInstance, List<Lash>> LASHES = new WeakHashMap<>();
    private static final Map<AnomalyInstance, Integer> NEXT_LASH = new WeakHashMap<>();

    private AmoebaEngine() {
    }

    public static Vec3 base(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        Vec3 c = zone.getCenter();
        Double ground = Razlom.groundY(level, c.x, c.z, zone.maxY, zone.minY - 3.0);
        return new Vec3(c.x, ground != null ? ground : zone.minY, c.z);
    }

    private static int attackTicks() {
        return AnomalyDefaults.ticks(ModCommonConfig.AMOEBA_ATTACK_SECONDS.get());
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        if (instance.active()) {
            tickActive(level, instance);
            return;
        }
        if (instance.cooldownTicks() > 0) {
            instance.setCooldownTicks(instance.cooldownTicks() - 1);
            if (instance.cooldownTicks() == 0) AnomalySyncHandler.broadcastState(level, instance);
            return;
        }
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        if (!level.getEntitiesOfClass(LivingEntity.class, zone, AmoebaEngine::targetable).isEmpty()) {
            instance.setActive(true);
            instance.setPulseTicks(0);
            NEXT_LASH.put(instance, Amoeba.GATHER_TICKS);
            LASHES.put(instance, new ArrayList<>());
            AnomalySyncHandler.broadcastState(level, instance);
            AmoebaEventPacket.send(level, instance.pos(), AmoebaEventPacket.GATHER, Vec3.ZERO, Vec3.ZERO);
            AnomalyCombat.playSound(level, base(level, instance), Amoeba.GATHER_SOUND, 1.2f);
        }
    }

    private static void tickActive(ServerLevel level, AnomalyInstance instance) {
        int t = instance.pulseTicks() + 1;
        instance.setPulseTicks(t);
        int attackEnd = Amoeba.GATHER_TICKS + attackTicks();
        long now = level.getGameTime();
        Vec3 base = base(level, instance);
        double dome = Amoeba.domeRadius(instance.size());
        List<Lash> lashes = LASHES.computeIfAbsent(instance, k -> new ArrayList<>());

        if (t >= Amoeba.GATHER_TICKS && t < attackEnd - Amoeba.LASH_EXTEND) {
            int next = NEXT_LASH.getOrDefault(instance, t);
            if (t >= next) {
                launch(level, instance, base, dome, lashes, now);
                int intensity = Math.max(1, instance.intensity());
                int gap = Math.max(2, 9 - intensity / 2) + level.random.nextInt(4);
                NEXT_LASH.put(instance, t + gap);
            }
        }

        for (Iterator<Lash> it = lashes.iterator(); it.hasNext(); ) {
            Lash lash = it.next();
            if (now < lash.hitTick()) continue;
            it.remove();
            hit(level, instance, lash);
        }

        if (t >= attackEnd + Amoeba.SETTLE_TICKS) {
            lashes.clear();
            instance.setActive(false);
            instance.setPulseTicks(0);
            instance.setCooldownTicks(AnomalyDefaults.ticks(instance.cooldownSeconds()));
            AnomalySyncHandler.broadcastState(level, instance);
            AmoebaEventPacket.send(level, instance.pos(), AmoebaEventPacket.SETTLE, Vec3.ZERO, Vec3.ZERO);
        } else if (t == attackEnd) {
            AnomalyCombat.playSound(level, base, Amoeba.SETTLE_SOUND, 1.0f);
        }
    }

    private static void launch(ServerLevel level, AnomalyInstance instance, Vec3 base, double dome, List<Lash> lashes, long now) {
        double reach = Amoeba.reach(instance.size());
        Vec3 top = base.add(0.0, dome, 0.0);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(top, top).inflate(reach + 1.0),
                e -> targetable(e) && e.getBoundingBox().getCenter().distanceTo(top) <= reach + 0.5);
        Vec3 tip;
        if (!near.isEmpty() && level.random.nextDouble() < Amoeba.AIMED_SHARE) {
            LivingEntity target = near.get(level.random.nextInt(near.size()));
            tip = target.getBoundingBox().getCenter();
        } else {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            double d = reach * (0.4 + 0.6 * level.random.nextDouble());
            double x = base.x + Math.cos(a) * d;
            double z = base.z + Math.sin(a) * d;
            Double ground = Razlom.groundY(level, x, z, base.y + 2.0, base.y - 3.0);
            tip = new Vec3(x, ground != null ? ground + 0.05 : base.y + 0.05, z);
        }
        // Not further than it reaches.
        Vec3 origin = Amoeba.lashOrigin(base, dome, tip);
        Vec3 to = tip.subtract(origin);
        if (to.length() > reach) tip = origin.add(to.normalize().scale(reach));
        lashes.add(new Lash(origin, tip, now + Amoeba.LASH_EXTEND));
        AmoebaEventPacket.send(level, instance.pos(), AmoebaEventPacket.LASH, origin, tip);
        AnomalyCombat.playSound(level, origin, Amoeba.LASH_SOUND, 0.8f, 0.85f + level.random.nextFloat() * 0.3f);
    }

    /** Whoever the lash passes through at full length is hit — whoever stepped aside isn't. */
    private static void hit(ServerLevel level, AnomalyInstance instance, Lash lash) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 2; i <= 10; i++) points.add(Amoeba.lashPoint(lash.origin(), lash.tip(), i / 10.0));
        AABB bounds = new AABB(lash.origin(), lash.tip()).inflate(1.5);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, bounds, AmoebaEngine::targetable)) {
            AABB box = victim.getBoundingBox().inflate(Amoeba.LASH_HIT_RADIUS);
            boolean touched = false;
            for (Vec3 p : points) {
                if (box.contains(p)) {
                    touched = true;
                    break;
                }
            }
            if (!touched) continue;
            victim.invulnerableTime = 0;
            AnomalyCombat.hurt(level, victim, Amoeba.DAMAGE_TYPE, instance.damage(), lash.origin());
            corrode(victim, ModCommonConfig.AMOEBA_ARMOR_CORROSION.get());
        }
    }

    /** The acid eats into every piece of armour worn. */
    private static void corrode(LivingEntity victim, int amount) {
        if (amount <= 0) return;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = victim.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;
            stack.hurtAndBreak(amount, victim, e -> e.broadcastBreakEvent(slot));
        }
    }

    private static boolean targetable(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        return !(entity instanceof Player player && player.isCreative());
    }

    public static void forget(AnomalyInstance instance) {
        LASHES.remove(instance);
        NEXT_LASH.remove(instance);
    }
}
