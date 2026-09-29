package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The second Tesla hit, landing when a struck entity's electrification ends. It lives outside the
 * Tesla on purpose: the Tesla pops at the moment of contact, so it isn't around to deliver it.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class TeslaShockScheduler {

    private record Pending(ResourceKey<Level> dimension, int entityId, long dueGameTime) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private TeslaShockScheduler() {
    }

    public static void schedule(ServerLevel level, LivingEntity target, int delayTicks) {
        PENDING.add(new Pending(level.dimension(), target.getId(), level.getGameTime() + delayTicks));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        if (!(event.level instanceof ServerLevel level)) return;

        long now = level.getGameTime();
        TeslaConfig config = TeslaConfigManager.get();
        List<LivingEntity> due = new ArrayList<>();

        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (!p.dimension().equals(level.dimension()) || now < p.dueGameTime()) continue;
            it.remove();
            Entity entity = level.getEntity(p.entityId());
            if (entity instanceof LivingEntity living && TeslaCombat.isValidTarget(living)) {
                due.add(living);
            }
        }

        for (LivingEntity target : due) {
            // The first hit left the target with invulnerability frames; with a short
            // electrify_ticks they'd silently swallow the second hit, so reset them first.
            target.invulnerableTime = 0;
            TeslaCombat.hurt(level, target, config.damageType(), config.damage());
            TeslaCombat.playRandomHit(level, target.getBoundingBox().getCenter(), config);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
    }
}
