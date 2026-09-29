package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.RustPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server side of Rust. Whoever walks in it without sneaking raises rusty dust at their feet — a
 * small cloud that burns (chemical, {@link ChemClouds}). Once the charge interval (the cooldown
 * tuner) has passed, a raised puff may be charged: it settles and heats a patch of the moss red-hot
 * for {@code spotSeconds} (one per anomaly; it takes a few seconds to heat up). Stepping onto it: a
 * blast of molten rust — heavy damage, a burn that goes on, every piece of armour badly worn. A
 * snowball thrown into it cools it with a hiss.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class RustEngine {

    public static final ResourceLocation BURN_DAMAGE_TYPE = new ResourceLocation(ZoneArtifacts.MODID, "anomaly_rust");
    public static final ResourceLocation DUST_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "rust_dust");
    public static final ResourceLocation BLAST_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "rust_blast");
    public static final ResourceLocation HISS_SOUND = new ResourceLocation(ZoneArtifacts.MODID, "rust_hiss");
    /** The patch needs this long to heat up before it goes off. */
    public static final int ARM_TICKS = 60;
    private static final int DUST_EVERY = 10;
    private static final double DUST_RADIUS = 1.1;
    private static final double DUST_SECONDS = 2.5;

    private static final class Spot {
        Vec3 at;
        long born;
        long dies;
    }

    private static final Map<AnomalyInstance, Spot> SPOTS = new WeakHashMap<>();
    private static final Map<AnomalyInstance, Long> LAST_CHARGE = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> LAST_DUST = new WeakHashMap<>();
    private static final Map<LivingEntity, int[]> BURNS = new WeakHashMap<>();

    private RustEngine() {
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        long now = level.getGameTime();
        Spot spot = SPOTS.get(instance);
        LAST_CHARGE.putIfAbsent(instance, now);

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, RustEngine::walker)) {
            double moved = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
            if (!e.onGround() || e.isCrouching() || moved < 0.04) continue;
            Long last = LAST_DUST.get(e);
            if (last != null && now - last < DUST_EVERY) continue;
            LAST_DUST.put(e, now);
            Vec3 feet = e.position();
            ChemClouds.add(level, feet, DUST_RADIUS, DUST_SECONDS, instance.damage());
            if (level.random.nextInt(3) == 0) {
                AnomalyCombat.playSound(level, feet, DUST_SOUND, 0.35f, 0.8f + level.random.nextFloat() * 0.4f);
            }
            if (spot == null && now - LAST_CHARGE.get(instance) >= AnomalyDefaults.ticks(instance.cooldownSeconds())
                    && level.random.nextDouble() < ModCommonConfig.RUST_CHARGE_CHANCE.get()) {
                spot = new Spot();
                // It settles a little way off (it drifts before it lands).
                spot.at = new Vec3(feet.x + level.random.nextGaussian() * 0.6, feet.y, feet.z + level.random.nextGaussian() * 0.6);
                spot.born = now;
                spot.dies = now + AnomalyDefaults.ticks(ModCommonConfig.RUST_SPOT_SECONDS.get());
                SPOTS.put(instance, spot);
                LAST_CHARGE.put(instance, now);
                RustPacket.send(level, instance.pos(), RustPacket.SPOT, spot.at, 0);
            }
        }

        if (spot == null) return;
        final Spot current = spot;
        if (now >= current.dies) {
            SPOTS.remove(instance);
            RustPacket.send(level, instance.pos(), RustPacket.CLEAR, current.at, 0);
            return;
        }
        double r = ModCommonConfig.RUST_SPOT_RADIUS.get();
        AABB area = new AABB(current.at.x - r, current.at.y - 0.5, current.at.z - r, current.at.x + r, current.at.y + 1.5, current.at.z + r);
        List<Snowball> snow = level.getEntitiesOfClass(Snowball.class, area, s -> s.isAlive() && horizontal(s.position(), current.at) <= r);
        if (!snow.isEmpty()) {
            snow.forEach(s -> s.discard());
            SPOTS.remove(instance);
            LAST_CHARGE.put(instance, now);
            AnomalyCombat.playSound(level, current.at, HISS_SOUND, 1.2f, 0.9f + level.random.nextFloat() * 0.2f);
            level.sendParticles(ParticleTypes.CLOUD, current.at.x, current.at.y + 0.3, current.at.z, 16, r * 0.5, 0.2, r * 0.5, 0.03);
            RustPacket.send(level, instance.pos(), RustPacket.DISCHARGE, current.at, 0);
            return;
        }
        if (now - current.born >= ARM_TICKS) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, RustEngine::walker)) {
                if (horizontal(e.position(), current.at) > r || Math.abs(e.getY() - current.at.y) > 0.8 || !e.onGround()) continue;
                blast(level, instance, current, r);
                return;
            }
        }
        if ((now - current.born) % 100 == 0) {
            // For whoever came near meanwhile.
            RustPacket.send(level, instance.pos(), RustPacket.SPOT, current.at, (int) (now - current.born));
        }
    }

    private static void blast(ServerLevel level, AnomalyInstance instance, Spot spot, double r) {
        SPOTS.remove(instance);
        LAST_CHARGE.put(instance, level.getGameTime());
        AnomalyCombat.playSound(level, spot.at, BLAST_SOUND, 2.0f, 0.9f + level.random.nextFloat() * 0.2f);
        RustPacket.send(level, instance.pos(), RustPacket.BLAST, spot.at, 0);
        AABB area = new AABB(spot.at, spot.at).inflate(r + 0.6, 1.5, r + 0.6);
        float blastDamage = ModCommonConfig.RUST_BLAST_DAMAGE.get().floatValue();
        int burnTicks = AnomalyDefaults.ticks(ModCommonConfig.RUST_BURN_SECONDS.get());
        double wear = ModCommonConfig.RUST_ARMOR_WEAR.get();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, RustEngine::walker)) {
            if (horizontal(e.position(), spot.at) > r + 0.6) continue;
            e.invulnerableTime = 0;
            AnomalyCombat.hurt(level, e, BURN_DAMAGE_TYPE, blastDamage, spot.at);
            e.setSecondsOnFire(2);
            if (burnTicks > 0) BURNS.put(e, new int[]{burnTicks});
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
                ItemStack armor = e.getItemBySlot(slot);
                if (armor.isEmpty() || !armor.isDamageableItem()) continue;
                int amount = (int) Math.ceil(armor.getMaxDamage() * wear);
                if (amount > 0) armor.hurtAndBreak(amount, e, x -> x.broadcastBreakEvent(slot));
            }
        }
    }

    private static boolean walker(LivingEntity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        return !(e instanceof Player p && p.isCreative());
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** The molten rust stuck on them keeps burning. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || BURNS.isEmpty()) return;
        float damage = ModCommonConfig.RUST_BURN_DAMAGE.get().floatValue();
        for (Map.Entry<LivingEntity, int[]> entry : new ArrayList<>(BURNS.entrySet())) {
            LivingEntity e = entry.getKey();
            if (e.level() != level) continue;
            int[] left = entry.getValue();
            if (!e.isAlive() || --left[0] <= 0) {
                BURNS.remove(e);
                continue;
            }
            if (left[0] % 10 == 0) {
                e.invulnerableTime = 0;
                AnomalyCombat.hurt(level, e, BURN_DAMAGE_TYPE, damage);
                level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 4,
                        e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0.01);
            }
        }
    }
}
