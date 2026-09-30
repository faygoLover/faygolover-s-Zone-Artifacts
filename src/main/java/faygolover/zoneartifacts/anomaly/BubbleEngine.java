package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.BubblePopPacket;
import faygolover.zoneartifacts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Soap bubbles: keeps each zone stocked with bubbles ({@link BubbleEntity}) — after its first pause
 * (the cooldown tuner) one every {@code respawnSeconds} until there are as many as the effects tuner
 * says; after a burst the pause starts again. A burst: a blow to everything in its radius (radius and
 * damage both from the damage tuner) and every bubble in reach charges too (a chain, a few ticks per
 * link, across neighbouring zones as well).
 */
public final class BubbleEngine {

    public static final ResourceLocation POP_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "bubble_pop");
    public static final int CHARGE_TICKS = 5;
    public static final int CHAIN_TICKS = 3;

    private static final Map<AnomalyInstance, Long> NEXT_SPAWN = new WeakHashMap<>();

    private BubbleEngine() {
    }

    /** Burst radius: the targeting tuner (bubbles placed before it had one grew it with the damage). */
    public static double radius(AnomalyInstance instance) {
        if (instance.range() > 0.0) return Mth.clamp(instance.range(), 0.5, 8.0);
        return Mth.clamp(1.2 + 0.2 * instance.damage(), 1.0, 6.0);
    }

    @Nullable
    public static AnomalyInstance instanceFor(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) return null;
        for (AnomalyInstance instance : AnomalySavedData.get(level).instances()) {
            if (instance.pos().equals(pos) && AnomalyTypeIds.BUBBLES.equals(instance.typeId()) && instance.enabled()) return instance;
        }
        return null;
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        long now = level.getGameTime();
        Long next = NEXT_SPAWN.get(instance);
        if (next == null) {
            NEXT_SPAWN.put(instance, now + AnomalyDefaults.ticks(instance.cooldownSeconds()));
            return;
        }
        if (now < next || now % 5 != 0) return;
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        if (!level.hasChunkAt(instance.pos())) return;
        int count = level.getEntitiesOfClass(BubbleEntity.class, zone.inflate(3.0), b -> instance.pos().equals(b.zone())).size();
        if (count >= Math.max(1, instance.intensity())) return;
        spawn(level, instance, zone);
        NEXT_SPAWN.put(instance, now + AnomalyDefaults.ticks(ModCommonConfig.BUBBLES_RESPAWN_SECONDS.get()));
    }

    private static void spawn(ServerLevel level, AnomalyInstance instance, AABB zone) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double x = Mth.lerp(level.random.nextDouble(), zone.minX + 0.5, zone.maxX - 0.5);
            double y = Mth.lerp(level.random.nextDouble(), zone.minY + 0.3, zone.maxY - 0.8);
            double z = Mth.lerp(level.random.nextDouble(), zone.minZ + 0.5, zone.maxZ - 0.5);
            BubbleEntity bubble = ModEntities.BUBBLE.get().create(level);
            if (bubble == null) return;
            bubble.moveTo(x, y, z, 0.0f, 0.0f);
            if (!level.noCollision(bubble, bubble.getBoundingBox())) continue;
            bubble.setZone(instance.pos());
            level.addFreshEntity(bubble);
            return;
        }
    }

    public static void burst(ServerLevel level, BubbleEntity bubble, AnomalyInstance instance) {
        Vec3 c = bubble.position().add(0.0, bubble.getBbHeight() / 2.0, 0.0);
        float damage = instance.damage();
        double r = radius(instance);
        bubble.discard();
        long now = level.getGameTime();
        NEXT_SPAWN.put(instance, Math.max(NEXT_SPAWN.getOrDefault(instance, now), now + AnomalyDefaults.ticks(instance.cooldownSeconds())));
        AnomalyCombat.playSound(level, c, POP_SOUND, 1.3f, 0.9f + level.random.nextFloat() * 0.25f);
        BubblePopPacket.send(level, c, (float) r);
        AABB area = new AABB(c, c).inflate(r);
        for (Entity e : level.getEntities((Entity) null, area, x -> x.isAlive() && !AnomalyCombat.spectatorExempt(x))) {
            if (e instanceof BubbleEntity other) {
                if (other.position().distanceTo(bubble.position()) <= r) other.trigger(CHAIN_TICKS);
                continue;
            }
            Vec3 to = e.getBoundingBox().getCenter().subtract(c);
            double d = to.length();
            if (d > r) continue;
            double k = 1.0 - d / r;
            Vec3 dir = d < 1.0E-3 ? new Vec3(0, 1, 0) : to.scale(1.0 / d);
            if (Gravity.movable(e)) {
                e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(0.5 + 0.8 * k)).add(0.0, 0.15 * k, 0.0));
                e.hurtMarked = true;
                e.hasImpulse = true;
            }
            if (e instanceof LivingEntity living && !AnomalyCombat.creativeExempt(living)) {
                living.invulnerableTime = 0;
                AnomalyCombat.hurt(level, living, Gravity.GRAVITY_DAMAGE_TYPE, (float) (damage * (0.4 + 0.6 * k)), c);
            }
        }
    }
}
