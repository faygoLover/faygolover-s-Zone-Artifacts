package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.AmoebaEventPacket;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import faygolover.zoneartifacts.network.ChemBurstPacket;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.tesla.ChemComet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server side of the Amoeba ({@link Amoeba}): the trigger, contact burns while it swells, the
 * burst (damage around + a lingering cloud, {@link ChemClouds}) and the cooldown. The client is told
 * when it starts and when it bursts; the cloud looks like the Chemical Comet's.
 */
public final class AmoebaEngine {

    private AmoebaEngine() {
    }

    public static Vec3 base(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        Vec3 c = zone.getCenter();
        Double ground = Razlom.groundY(level, c.x, c.z, zone.maxY, zone.minY - 3.0);
        return new Vec3(c.x, ground != null ? ground : zone.minY, c.z);
    }

    public static int inflateTicks() {
        return Math.max(Amoeba.GATHER_TICKS + Amoeba.LIFT_TICKS + 10, AnomalyDefaults.ticks(ModCommonConfig.AMOEBA_INFLATE_SECONDS.get()));
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
            AnomalySyncHandler.broadcastState(level, instance);
            AmoebaEventPacket.send(level, instance.pos(), AmoebaEventPacket.GATHER);
            AnomalyCombat.playSound(level, base(level, instance), Amoeba.GATHER_SOUND, 1.2f);
        }
    }

    private static void tickActive(ServerLevel level, AnomalyInstance instance) {
        int t = instance.pulseTicks() + 1;
        instance.setPulseTicks(t);
        int inflate = inflateTicks();
        Vec3 base = base(level, instance);
        double size = instance.size();
        Vec3 center = base.add(0.0, Amoeba.centerHeight(size, t, inflate), 0.0);
        double radius = Amoeba.radius(size, t, inflate);

        if (t == inflate - Amoeba.POP_SOUND_LEAD) AnomalyCombat.playSound(level, center, Amoeba.POP_SOUND, 2.0f);

        // Touching the swelling jelly burns.
        if (t % Amoeba.CONTACT_INTERVAL == 0) {
            float contact = ModCommonConfig.AMOEBA_CONTACT_DAMAGE.get().floatValue();
            AABB ball = new AABB(center, center).inflate(radius + 0.1);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, ball, AmoebaEngine::targetable)) {
                victim.invulnerableTime = 0;
                AnomalyCombat.hurt(level, victim, Amoeba.DAMAGE_TYPE, contact, center);
            }
        }

        if (t >= inflate) burst(level, instance, center, radius);
    }

    private static void burst(ServerLevel level, AnomalyInstance instance, Vec3 center, double radius) {
        double cloud = Math.min(ChemComet.MAX_CLOUD_RADIUS * Amoeba.CLOUD_SCALE,
                ChemComet.cloudRadius(instance.size()) * Amoeba.CLOUD_SCALE);
        double reach = cloud * ChemComet.BURST_SHARE;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(reach), AmoebaEngine::targetable)) {
            double d = victim.getBoundingBox().getCenter().distanceTo(center);
            if (d > reach) continue;
            victim.invulnerableTime = 0;
            AnomalyCombat.hurt(level, victim, Amoeba.DAMAGE_TYPE, (float) (instance.damage() * Gravity.falloff(d, reach, 0.3)), center);
        }
        Double ground = Razlom.groundY(level, center.x, center.z, center.y + 0.5, center.y - 8.0);
        double groundY = ground != null ? ground : center.y - 1.0;
        double seconds = ModCommonConfig.AMOEBA_CLOUD_SECONDS.get();
        ChemClouds.add(level, new Vec3(center.x, groundY, center.z), cloud, seconds, ModCommonConfig.AMOEBA_CLOUD_DAMAGE.get().floatValue());
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(center.x, center.y, center.z, 96.0, level.dimension())),
                new ChemBurstPacket(center, groundY, (float) cloud, (float) seconds, instance.intensity()));
        AmoebaEventPacket.send(level, instance.pos(), AmoebaEventPacket.POP);

        instance.setActive(false);
        instance.setPulseTicks(0);
        instance.setCooldownTicks(AnomalyDefaults.ticks(instance.cooldownSeconds()));
        AnomalySyncHandler.broadcastState(level, instance);
    }

    private static boolean targetable(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        return !(entity instanceof Player player && player.isCreative());
    }

    public static void forget(AnomalyInstance instance) {
    }
}
