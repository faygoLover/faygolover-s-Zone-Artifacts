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
import faygolover.zoneartifacts.config.ModCommonConfig;
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
    /** How far it has spread, 0..1: grows while it seethes, shrinks back after (same pace as the look). */
    private static final Map<AnomalyInstance, Float> GROW = new WeakHashMap<>();
    private static final Map<AnomalyInstance, Integer> HISS = new WeakHashMap<>();
    /** How long each item has been soaking (items dissolve slowly, one at a time). */
    private static final Map<ItemEntity, Integer> SOAK = new WeakHashMap<>();

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
        Vec3 c = zone.getCenter();
        long seed = instance.pos().asLong();
        Kisel.Tongue[] tongues = Kisel.tongues(seed);
        // Waking: only within its react circle, which never grows.
        double react = Kisel.reactRadius(instance.size());
        AABB reactBox = new AABB(c.x - react, surface - 0.6, c.z - react, c.x + react, surface + Kisel.CONTACT_HEIGHT, c.z + react);
        boolean touched = !level.getEntities((Entity) null, reactBox,
                e -> eaten(e) && Math.hypot(e.getX() - c.x, e.getZ() - c.z) <= react + e.getBbWidth() / 2.0).isEmpty();
        float grow = GROW.getOrDefault(instance, 0.0f);
        grow = instance.active() ? Math.min(1.0f, grow + 0.08f) : Math.max(0.0f, grow - 0.02f);
        GROW.put(instance, grow);
        // Eating: all over the puddle as it is now (it spreads while seething).
        double reach = Kisel.maxReach(instance.size());
        AABB puddleBox = new AABB(c.x - reach, surface - 0.6, c.z - reach, c.x + reach, surface + Kisel.CONTACT_HEIGHT, c.z + reach);
        final float g = grow;
        List<Entity> in = level.getEntities((Entity) null, puddleBox, e -> eaten(e)
                && Kisel.inPuddle(c.x, c.z, instance.size(), seed, tongues, g, e.getX(), e.getZ(), e.getBbWidth() / 2.0));

        int calm = CALM.getOrDefault(instance, 0);
        if (touched || (instance.active() && !in.isEmpty())) {
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

        // Items: each soaks on its own clock, fuming now and then, and loses one at a time.
        int itemTicks = Math.max(1, AnomalyDefaults.ticks(ModCommonConfig.KISEL_ITEM_SECONDS.get()));
        for (Entity e : in) {
            if (!(e instanceof ItemEntity item)) continue;
            int soak = SOAK.getOrDefault(item, 0) + 1;
            if (soak % 15 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.1, item.getZ(), 1, 0.05, 0.03, 0.05, 0.005);
            }
            if (soak >= itemTicks) {
                soak = 0;
                ItemStack stack = item.getItem().copy();
                stack.shrink(1);
                if (stack.isEmpty()) item.discard();
                else item.setItem(stack);
                level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.1, item.getZ(), 3, 0.08, 0.05, 0.08, 0.01);
            }
            SOAK.put(item, soak);
        }

        int t = instance.pulseTicks() + 1;
        instance.setPulseTicks(t);
        if (t < AnomalyDefaults.ticks(instance.cooldownSeconds()) || in.isEmpty()) return;
        instance.setPulseTicks(0);
        for (Entity e : in) {
            if (e instanceof ItemEntity) {
                continue;
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
        if (!entity.isAlive() || AnomalyCombat.spectatorExempt(entity)) return false;
        if (AnomalyCombat.creativeExempt(entity)) return false;
        return entity instanceof ItemEntity || entity instanceof LivingEntity
                || (entity instanceof Projectile && !(entity instanceof net.minecraft.world.entity.projectile.FishingHook));
    }
}
