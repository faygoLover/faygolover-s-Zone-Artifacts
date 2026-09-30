package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Kamerton: a ball of glass needles (diameter = size). Whoever moves in it without sneaking is cut
 * ({@code anomaly_cut}; armour helps) every {@code cooldown} seconds, harder when running; sneaking
 * through — or standing still — is safe. Projectiles shatter on it. "Active" while anything moves
 * in it: the needles ring louder.
 */
public final class KamertonEngine {

    public static final ResourceLocation DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_cut");
    public static final ResourceLocation CUT_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "kamerton_cut");
    public static final ResourceLocation SHATTER_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "kamerton_shatter");

    private static final Map<LivingEntity, Long> LAST_CUT = new WeakHashMap<>();
    private static final Map<AnomalyInstance, Integer> CALM = new WeakHashMap<>();

    private KamertonEngine() {
    }

    public static Vec3 center(AnomalyInstance instance) {
        return AnomalyGeometry.zoneAabb(instance).getCenter();
    }

    /** Does the box reach into the ball? */
    public static boolean inside(AABB box, Vec3 c, double r) {
        double x = Math.max(box.minX, Math.min(c.x, box.maxX));
        double y = Math.max(box.minY, Math.min(c.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(c.z, box.maxZ));
        double dx = x - c.x;
        double dy = y - c.y;
        double dz = z - c.z;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        Vec3 c = zone.getCenter();
        double r = instance.size() / 2.0;
        long now = level.getGameTime();
        boolean moving = false;

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, x -> x.isAlive() && !AnomalyCombat.spectatorExempt(x))) {
            if (!inside(e.getBoundingBox(), c, r)) continue;
            double speed = Motion.moved(e, now);
            boolean sneaking = e.isCrouching();
            if (speed < 0.03 || (sneaking && speed < 0.12)) continue;
            moving = true;
            if (AnomalyCombat.creativeExempt(e)) continue;
            Long last = LAST_CUT.get(e);
            if (last != null && now - last < AnomalyDefaults.ticks(instance.cooldownSeconds())) continue;
            LAST_CUT.put(e, now);
            float damage = instance.damage();
            if (e.isSprinting() || speed > 0.2) damage *= ModCommonConfig.KAMERTON_SPRINT_MULTIPLIER.get().floatValue();
            e.invulnerableTime = 0;
            AnomalyCombat.hurt(level, e, DAMAGE_TYPE, damage, c);
            AnomalyCombat.playSound(level, e.position().add(0, e.getBbHeight() * 0.5, 0), CUT_SOUND, 0.8f, 0.9f + level.random.nextFloat() * 0.3f);
        }

        List<Projectile> flying = level.getEntitiesOfClass(Projectile.class, zone, p -> p.isAlive() && Gravity.flying(p));
        for (Projectile p : flying) {
            if (!inside(p.getBoundingBox(), c, r)) continue;
            Vec3 at = p.position();
            p.discard();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), at.x, at.y, at.z,
                    18, 0.15, 0.15, 0.15, 0.15);
            AnomalyCombat.playSound(level, at, SHATTER_SOUND, 1.0f, 0.9f + level.random.nextFloat() * 0.3f);
        }

        // Ringing louder while something moves in it (with a short calm-down).
        int calm = CALM.getOrDefault(instance, 0);
        calm = moving ? 30 : Math.max(0, calm - 1);
        CALM.put(instance, calm);
        boolean active = calm > 0;
        if (active != instance.active()) {
            instance.setActive(active);
            AnomalySyncHandler.broadcastState(level, instance);
        }
    }
}
