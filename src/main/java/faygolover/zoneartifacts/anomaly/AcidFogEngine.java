package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.FogJetPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server side of the Acid Fog ({@link AcidFog}): slow burns inside, and a jet every
 * {@code cooldown} seconds (give or take a third) at a random spot of it.
 */
public final class AcidFogEngine {

    private static final Map<AnomalyInstance, Integer> NEXT_JET = new WeakHashMap<>();

    private AcidFogEngine() {
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        int t = instance.pulseTicks() + 1;
        instance.setPulseTicks(t);

        if (t % AcidFog.IN_FOG_INTERVAL == 0) {
            float damage = ModCommonConfig.FOG_DAMAGE.get().floatValue();
            if (damage > 0.0f) {
                for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, zone, AcidFogEngine::hurtable)) {
                    living.invulnerableTime = 0;
                    AnomalyCombat.hurt(level, living, AcidFog.DAMAGE_TYPE, damage, zone.getCenter());
                }
            }
        }

        // The speed tuner is the jets' frequency (x0: none at all).
        if (instance.speed() <= 0.001) {
            NEXT_JET.remove(instance);
            return;
        }
        int next = NEXT_JET.computeIfAbsent(instance, k -> t + interval(level, instance));
        if (t < next) return;
        NEXT_JET.put(instance, t + interval(level, instance));
        double x = Mth.lerp(level.random.nextDouble(), zone.minX + 0.3, zone.maxX - 0.3);
        double z = Mth.lerp(level.random.nextDouble(), zone.minZ + 0.3, zone.maxZ - 0.3);
        Double ground = Razlom.groundY(level, x, z, zone.maxY, zone.minY - 3.0);
        double y = ground != null ? ground : zone.minY;
        Vec3 at = new Vec3(x, y, z);
        AABB column = new AABB(x - AcidFog.JET_RADIUS, y - 0.2, z - AcidFog.JET_RADIUS, x + AcidFog.JET_RADIUS, y + AcidFog.JET_HEIGHT, z + AcidFog.JET_RADIUS);
        for (Entity e : level.getEntities((Entity) null, column, Gravity::movable)) {
            double dx = e.getX() - x;
            double dz = e.getZ() - z;
            if (dx * dx + dz * dz > AcidFog.JET_RADIUS * AcidFog.JET_RADIUS) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(0.0, AcidFog.JET_PUSH, 0.0));
            e.hurtMarked = true;
            e.hasImpulse = true;
            if (e instanceof LivingEntity living && hurtable(living)) {
                living.invulnerableTime = 0;
                AnomalyCombat.hurt(level, living, AcidFog.DAMAGE_TYPE, instance.damage(), at);
            }
        }
        FogJetPacket.send(level, at, instance.intensity());
        AnomalyCombat.playSound(level, at.add(0, 1, 0), AcidFog.JET_SOUND, 1.5f, 0.85f + level.random.nextFloat() * 0.3f);
    }

    private static int interval(ServerLevel level, AnomalyInstance instance) {
        double base = AnomalyDefaults.ticks(instance.cooldownSeconds()) / Math.max(0.05, instance.speed());
        return Math.max(5, (int) (base * (0.67 + level.random.nextDouble() * 0.66)));
    }

    private static boolean hurtable(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        return !(entity instanceof Player player && player.isCreative());
    }
}
