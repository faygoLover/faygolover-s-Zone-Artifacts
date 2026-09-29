package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Burning Fluff's spore puffs, server side: each flies straight at where its target was, slowing
 * down, swelling, and stops at the first block it meets. Whoever it passes through is burnt
 * (chemical damage, once per puff). The client flies the same path for the look.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class PukhSpores {

    private static final class Cloud {
        final ResourceKey<Level> dimension;
        final Vec3 source;
        Vec3 pos;
        Vec3 vel;
        int age;
        boolean landed;
        final float damage;
        final Set<UUID> hit = new HashSet<>();

        Cloud(ResourceKey<Level> dimension, Vec3 pos, Vec3 vel, float damage) {
            this.dimension = dimension;
            this.source = pos;
            this.pos = pos;
            this.vel = vel;
            this.damage = damage;
        }

        double radius() {
            return 0.45 + Math.min(1.0, age / 20.0) * 0.55;
        }
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();

    private PukhSpores() {
    }

    /** A puff from {@code from} flying at {@code at}, reaching about {@code range} blocks. */
    public static void shoot(ServerLevel level, Vec3 from, Vec3 at, double range, float damage) {
        Vec3 dir = at.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) return;
        CLOUDS.add(new Cloud(level.dimension(), from, dir.normalize().scale(range * (1.0 - Pukh.SPORE_DRAG)), damage));
    }

    /** One tick of a puff's flight — the same on the client ({@code client.pukh.PukhClient}). */
    public static Vec3[] step(net.minecraft.world.level.BlockGetter level, Vec3 pos, Vec3 vel) {
        Vec3 next = pos.add(vel);
        BlockHitResult hit = Razlom.clipBlocks(level, pos, next);
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            return new Vec3[]{hit.getLocation().add(n.scale(0.15)), Vec3.ZERO, n};
        }
        return new Vec3[]{next, vel.scale(Pukh.SPORE_DRAG), null};
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || CLOUDS.isEmpty()) return;
        for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
            Cloud cloud = it.next();
            if (!cloud.dimension.equals(level.dimension())) continue;
            if (++cloud.age > Pukh.SPORE_LIFE) {
                it.remove();
                continue;
            }
            if (!cloud.landed) {
                Vec3[] s = step(level, cloud.pos, cloud.vel);
                cloud.pos = s[0];
                cloud.vel = s[1];
                if (s[2] != null) cloud.landed = true;
            }
            double r = cloud.radius();
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(cloud.pos, cloud.pos).inflate(r + 1.0), PukhSpores::hurtable)) {
                if (cloud.hit.contains(living.getUUID())) continue;
                if (!living.getBoundingBox().inflate(r * 0.6).contains(cloud.pos)) continue;
                cloud.hit.add(living.getUUID());
                living.invulnerableTime = 0;
                AnomalyCombat.hurt(level, living, Pukh.DAMAGE_TYPE, cloud.damage, cloud.source);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CLOUDS.clear();
    }

    private static boolean hurtable(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        return !(entity instanceof Player player && player.isCreative());
    }
}
