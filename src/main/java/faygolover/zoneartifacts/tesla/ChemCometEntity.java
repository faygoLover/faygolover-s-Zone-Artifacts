package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.anomaly.AnomalyCombat;
import faygolover.zoneartifacts.anomaly.ChemClouds;
import faygolover.zoneartifacts.anomaly.Razlom;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.ChemBurstPacket;
import faygolover.zoneartifacts.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * The Chemical Comet: a clot of gas flying a route (all the flying, chasing and respawning is the
 * Tesla's, {@link TeslaEntity}). On impact — a block or a living being — it bursts: chemical
 * damage around, then a heavy cloud settles over the ground ({@link ChemClouds}). See {@link ChemComet}.
 * Its look and its trail of gas are all client-side ({@code client.chem}).
 */
public class ChemCometEntity extends TeslaEntity {

    public ChemCometEntity(EntityType<? extends TeslaEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void onBlockCollision(ServerLevel level, TeslaRoute route, Vec3 normal) {
        burst(level, route, List.of());
    }

    @Override
    protected void shock(ServerLevel level, TeslaRoute route, List<LivingEntity> touched) {
        burst(level, route, touched);
    }

    private void burst(ServerLevel level, TeslaRoute route, List<LivingEntity> touched) {
        Vec3 c = center();
        double size = getSize();
        double radius = ChemComet.cloudRadius(size);
        double burst = radius * ChemComet.BURST_SHARE;

        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(burst), e -> e instanceof LivingEntity)) {
            LivingEntity living = (LivingEntity) e;
            if (!AnomalyCombat.isValidTeslaTarget(living)) continue;
            boolean direct = touched.contains(living);
            double d = living.getBoundingBox().getCenter().distanceTo(c);
            if (d > burst && !direct) continue;
            double falloff = direct ? 1.0 : 1.0 - Mth.clamp(d / burst, 0.0, 1.0);
            float damage = route.damage() * (float) (0.3 + 0.7 * falloff);
            living.invulnerableTime = 0;
            AnomalyCombat.hurt(level, living, ChemComet.DAMAGE_TYPE, damage, c);
        }

        // The gas is heavy: the cloud settles on the ground under the burst.
        Double ground = Razlom.groundY(level, c.x, c.z, c.y + 0.5, c.y - 8.0);
        double groundY = ground != null ? ground : c.y - 0.5;
        double seconds = ModCommonConfig.CHEM_COMET_CLOUD_SECONDS.get();
        ChemClouds.add(level, new Vec3(c.x, groundY, c.z), radius, seconds, ModCommonConfig.CHEM_COMET_CLOUD_DAMAGE.get().floatValue());
        if (ModCommonConfig.CHEM_COMET_KILLS_PLANTS.get()) {
            ChemClouds.wither(level, c, Math.min(radius, ChemComet.MAX_WITHER_RADIUS), level.random);
        }

        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(c.x, c.y, c.z, 96.0, level.dimension())),
                new ChemBurstPacket(c, groundY, (float) radius, (float) seconds, route.intensity()));
        AnomalyCombat.playSound(level, c, ChemComet.BURST_SOUND, ChemComet.BURST_VOLUME);
        die();
    }
}
