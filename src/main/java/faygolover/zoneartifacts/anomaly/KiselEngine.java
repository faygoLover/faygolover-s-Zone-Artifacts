package faygolover.zoneartifacts.anomaly;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import faygolover.zoneartifacts.network.AnomalySyncHandler;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server side of Kisel ({@link Kisel}). Every {@code cooldown} (its "damage interval", 0.5 s by
 * default) it eats into whatever is in it; it is "active" (seething, brighter, hissing) while
 * anything is and for a moment after.
 */
public final class KiselEngine {

    private static final Map<AnomalyInstance, Integer> CALM = new WeakHashMap<>();
    private static final Map<AnomalyInstance, Integer> HISS = new WeakHashMap<>();

    private KiselEngine() {
    }

    public static double surfaceY(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        Vec3 c = zone.getCenter();
        Double ground = Razlom.groundY(level, c.x, c.z, zone.maxY, zone.minY - 3.0);
        return (ground != null ? ground : zone.minY) + 0.06;
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        double surface = surfaceY(level, instance);
        AABB box = Kisel.contactBox(zone, surface);
        List<Entity> in = level.getEntities((Entity) null, box, KiselEngine::eaten);

        int calm = CALM.getOrDefault(instance, 0);
        if (!in.isEmpty()) {
            calm = Kisel.CALM_TICKS;
            if (!instance.active()) {
                instance.setActive(true);
                AnomalySyncHandler.broadcastState(level, instance);
                HISS.put(instance, 0);
            }
        } else if (calm > 0) {
            calm--;
            if (calm == 0 && instance.active()) {
                instance.setActive(false);
                AnomalySyncHandler.broadcastState(level, instance);
            }
        }
        CALM.put(instance, calm);

        if (instance.active() && !in.isEmpty()) {
            int hiss = HISS.getOrDefault(instance, 0);
            if (hiss <= 0) {
                Entity e = in.get(level.random.nextInt(in.size()));
                AnomalyCombat.playSound(level, new Vec3(e.getX(), surface, e.getZ()), Kisel.HIT_SOUND, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
                hiss = 40;
            }
            HISS.put(instance, hiss - 1);
        }

        int t = instance.pulseTicks() + 1;
        instance.setPulseTicks(t);
        if (t < AnomalyDefaults.ticks(instance.cooldownSeconds()) || in.isEmpty()) return;
        instance.setPulseTicks(0);
        for (Entity e : in) {
            if (e instanceof ItemEntity item) {
                ItemStack stack = item.getItem().copy();
                stack.shrink(1);
                if (stack.isEmpty()) item.discard();
                else item.setItem(stack);
                level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.1, item.getZ(), 3, 0.08, 0.05, 0.08, 0.01);
            } else if (e instanceof Projectile) {
                level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY(), e.getZ(), 4, 0.08, 0.05, 0.08, 0.01);
                e.discard();
            } else if (e instanceof LivingEntity living) {
                living.invulnerableTime = 0;
                AnomalyCombat.hurt(level, living, Kisel.DAMAGE_TYPE, instance.damage(), new Vec3(living.getX(), surface, living.getZ()));
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS}) {
                    ItemStack armor = living.getItemBySlot(slot);
                    if (!armor.isEmpty() && armor.isDamageableItem()) armor.hurtAndBreak(1, living, x -> x.broadcastBreakEvent(slot));
                }
            }
        }
    }

    private static boolean eaten(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        if (entity instanceof Player player && player.isCreative()) return false;
        return entity instanceof ItemEntity || entity instanceof LivingEntity
                || (entity instanceof Projectile && !(entity instanceof net.minecraft.world.entity.projectile.FishingHook));
    }
}
