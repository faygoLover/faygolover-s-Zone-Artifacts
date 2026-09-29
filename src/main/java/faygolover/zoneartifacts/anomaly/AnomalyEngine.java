package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.AnomalyStrikePacket;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import faygolover.zoneartifacts.network.ElectrifyPacket;
import faygolover.zoneartifacts.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side tick logic for placed zone anomalies. Electra is handled here (Zharka and Iney in
 * {@link ThermalEngine}): detecting entities
 * inside the zone, applying damage, sending the strike/electrify visuals, running the cooldown.
 * All values come from the anomaly itself ({@link AnomalyInstance}), set with the tuners.
 * <p>
 * Everything ambient (idle loop, arcs) is client-side, driven by the {@code onCooldown} flag this
 * class keeps synced via {@link AnomalySyncHandler#broadcastCooldown}.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public class AnomalyEngine {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        AnomalySavedData data = AnomalySavedData.get(serverLevel);
        for (AnomalyInstance instance : List.copyOf(data.instances())) {
            if (AnomalyTypeIds.ELECTRA.equals(instance.typeId())) {
                tickElectra(serverLevel, instance);
            } else if (AnomalyTypeIds.isThermal(instance.typeId())) {
                ThermalEngine.tick(serverLevel, instance);
            } else if (AnomalyTypeIds.isRazlom(instance.typeId())) {
                RazlomEngine.tick(serverLevel, instance);
            }
        }
    }

    private static void tickElectra(ServerLevel level, AnomalyInstance instance) {
        boolean wasOnCooldown = instance.cooldownTicks() > 0;

        // Always write the decremented value back, even once it reaches 0 — otherwise the field
        // would freeze on its last nonzero value forever.
        int cooldown = Math.max(0, instance.cooldownTicks() - 1);
        instance.setCooldownTicks(cooldown);

        if (wasOnCooldown && cooldown == 0) {
            AnomalySyncHandler.broadcastCooldown(level, instance, false);
        }
        if (cooldown > 0) return;

        AABB aabb = AnomalyGeometry.zoneAabb(instance);
        List<Entity> hits = level.getEntities((Entity) null, aabb, AnomalyEngine::tripsElectra);
        if (hits.isEmpty()) return;

        List<LivingEntity> hitLiving = new ArrayList<>();
        List<Projectile> hitProjectiles = new ArrayList<>();
        for (Entity entity : hits) {
            if (entity instanceof LivingEntity living) {
                if (AnomalyCombat.hurt(level, living, Electra.DAMAGE_TYPE, instance.damage())) {
                    hitLiving.add(living);
                }
            } else if (entity instanceof Projectile projectile) {
                // Thrown items (snowballs, eggs, ...) trip the anomaly and get struck, but take no damage.
                hitProjectiles.add(projectile);
            }
        }

        if (!hitLiving.isEmpty() || !hitProjectiles.isEmpty()) {
            playTriggerEffect(level, instance, aabb, hitLiving, hitProjectiles);
            instance.setCooldownTicks(AnomalyDefaults.ticks(instance.cooldownSeconds()));
            AnomalySyncHandler.broadcastCooldown(level, instance, true);
        }
    }

    private static boolean tripsElectra(Entity entity) {
        if (entity.isSpectator()) return false;
        return entity instanceof Player || entity instanceof LivingEntity || entity instanceof Projectile;
    }

    /**
     * The blast sound depends on what happened ({@code blast_living} if someone got shocked,
     * {@code blast_nut} if only a thrown item tripped it); each shocked entity also gets a close-up
     * hit sound from its own position. Visually: bolts from the zone's surfaces onto every struck
     * entity or projectile, and living targets are electrified once the bolts connect.
     */
    private static void playTriggerEffect(ServerLevel level, AnomalyInstance instance, AABB aabb,
                                          List<LivingEntity> hitLiving, List<Projectile> hitProjectiles) {
        List<Entity> struck = new ArrayList<>(hitLiving);
        struck.addAll(hitProjectiles);
        for (Entity target : struck) {
            ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension),
                    new AnomalyStrikePacket(instance.typeId(), instance.pos(), (float) instance.size(), instance.intensity(), target.getId()));
        }
        for (LivingEntity target : hitLiving) {
            ElectrifyPacket.send(target, ModCommonConfig.electrifyTicks(), Electra.STRIKE_WINDUP_TICKS, instance.intensity());
            AnomalyCombat.playRandom(level, target.position(), Electra.HIT_SOUNDS, Electra.HIT_VOLUME);
        }

        AnomalyCombat.playSound(level, aabb.getCenter(),
                hitLiving.isEmpty() ? Electra.BLAST_PROJECTILE_SOUND : Electra.BLAST_LIVING_SOUND, Electra.BLAST_VOLUME);
    }
}
