package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.anomaly.AnomalyCombat;
import faygolover.zoneartifacts.anomaly.ColdEffects;
import faygolover.zoneartifacts.anomaly.Thermal;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.CometBurstPacket;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Comet: a fireball flying a route. Everything about flying, chasing and respawning is the
 * Tesla's ({@link TeslaEntity}); only the impact differs — see {@link Comet} and {@link #explode}.
 * Client side it sheds a light trail of sparks and flames.
 * <p>
 * {@link ColdCometEntity} is the same in soul fire: where this one burns, that one freezes
 * ({@link ColdEffects}) — see {@link #isCold()}.
 */
public class CometEntity extends TeslaEntity {

    public CometEntity(EntityType<? extends TeslaEntity> type, Level level) {
        super(type, level);
    }

    /** Soul fire: freezes instead of burning, cold colours and particles. */
    public boolean isCold() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && getState().isVisible()) {
            clientTrail();
        }
    }

    @Override
    protected void onBlockCollision(ServerLevel level, TeslaRoute route, Vec3 normal) {
        explode(level, route, normal, List.of());
    }

    @Override
    protected void shock(ServerLevel level, TeslaRoute route, List<LivingEntity> touched) {
        explode(level, route, null, touched);
    }

    /**
     * @param normal  the struck wall's normal, or null when it exploded on an entity
     * @param touched entities it flew into (full damage and always set on fire)
     */
    private void explode(ServerLevel level, TeslaRoute route, @Nullable Vec3 normal, List<LivingEntity> touched) {
        Vec3 c = center();
        double size = getSize();
        double core = Comet.CORE_RADIUS * size;
        double blast = Comet.BLAST_RADIUS * size;
        RandomSource random = level.random;
        boolean cold = isCold();
        int igniteSeconds = cold ? ModCommonConfig.COLD_COMET_FREEZE_SECONDS.get() : ModCommonConfig.COMET_IGNITE_SECONDS.get();

        // ---- entities: push, damage, fire ----
        AABB box = new AABB(c, c).inflate(blast);
        for (Entity e : level.getEntities(this, box, e -> e.isAlive() && !e.isSpectator() && !(e instanceof TeslaEntity))) {
            Vec3 ec = e.getBoundingBox().getCenter();
            double d = ec.distanceTo(c);
            boolean direct = touched.contains(e);
            if (d > blast && !direct) continue;
            double falloff = direct ? 1.0 : 1.0 - Mth.clamp(d / blast, 0.0, 1.0);

            if (!(e instanceof Player player && player.isCreative() && player.getAbilities().flying)) {
                Vec3 dir = d < 1.0E-3 ? new Vec3(0, 1, 0) : ec.subtract(c).scale(1.0 / d);
                double k = Comet.KNOCKBACK * Math.sqrt(size) * (0.3 + 0.7 * falloff);
                if (e instanceof LivingEntity living) {
                    k *= 1.0 - Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
                }
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * k, dir.y * k * 0.6 + 0.25 * k, dir.z * k));
                e.hurtMarked = true;
            }

            if (e instanceof LivingEntity living && AnomalyCombat.isValidTeslaTarget(living)) {
                float damage = route.damage() * (direct ? 1.0f : Comet.EDGE_DAMAGE + (1.0f - Comet.EDGE_DAMAGE) * (float) falloff);
                AnomalyCombat.hurt(level, living, cold ? Thermal.COLD_DAMAGE_TYPE : Comet.DAMAGE_TYPE, damage);
            }

            boolean ignite = direct || d <= core || random.nextDouble() < Comet.OUTER_ENTITY_IGNITE_CHANCE;
            if (cold) {
                if (ignite) ColdEffects.freeze(e, igniteSeconds);
            } else if (ignite && igniteSeconds > 0 && !e.fireImmune()
                    && !(e instanceof Player player && player.isCreative())) {
                e.setSecondsOnFire(igniteSeconds);
            }
        }

        // ---- blocks: a few fires around the impact, never any damage ----
        // The impact spot itself first, then random spots: likelier near the center, never more
        // than a handful (2 + 2 x size) in total.
        // (The Cold Comet: the same few spots get frozen instead — ice, snow, fires put out.)
        if (cold ? ModCommonConfig.COLD_COMET_ALTERS_BLOCKS.get() : ModCommonConfig.COMET_IGNITES_BLOCKS.get()) {
            int maxFires = 2 + (int) Math.round(2.0 * size);
            BlockPos origin = BlockPos.containing(c);
            int fires = 0;
            if (alter(level, origin, cold)) fires++;

            double scan = Math.min(blast, Comet.MAX_BLOCK_RADIUS);
            double coreScan = Math.min(core, Comet.MAX_BLOCK_RADIUS);
            int r = Mth.ceil(scan);
            List<BlockPos> candidates = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        double d = Vec3.atCenterOf(pos).distanceTo(c);
                        if (d > scan) continue;
                        double chance = d <= coreScan ? Comet.CORE_BLOCK_IGNITE_CHANCE : Comet.OUTER_BLOCK_IGNITE_CHANCE;
                        if (random.nextDouble() < chance) candidates.add(pos);
                    }
                }
            }
            // Random order, so the cap doesn't always favour one corner.
            for (int i = candidates.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                BlockPos tmp = candidates.get(i);
                candidates.set(i, candidates.get(j));
                candidates.set(j, tmp);
            }
            for (BlockPos pos : candidates) {
                if (fires >= maxFires) break;
                if (alter(level, pos, cold)) fires++;
            }
        }

        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
                new CometBurstPacket(c, normal, getSize(), route.intensity(), cold));
        AnomalyCombat.playSound(level, c, Comet.EXPLODE_SOUND, Comet.EXPLODE_VOLUME, cold ? Comet.COLD_PITCH : 1.0f);
        die();
    }

    private static boolean alter(ServerLevel level, BlockPos pos, boolean cold) {
        return cold ? ColdEffects.chill(level, pos) : ignite(level, pos);
    }

    /** Fire in an empty spot where fire can stand; unlit campfires and candles light up.
     *  @return whether something was set alight */
    private static boolean ignite(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            if (BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                return true;
            }
            return false;
        }
        if ((state.getBlock() instanceof CampfireBlock || state.getBlock() instanceof AbstractCandleBlock)
                && state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)
                && !(state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))) {
            level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
            return true;
        }
        return false;
    }

    /** A light trail: sparks and small flames shed from the surface, drifting behind. */
    private void clientTrail() {
        RandomSource random = this.random;
        float size = getSize();
        int intensity = Math.max(1, getIntensity());
        Vec3 c = center();
        Vec3 motion = c.subtract(new Vec3(this.xo, this.yo + this.getBbHeight() / 2.0, this.zo));
        if (motion.lengthSqr() > 4.0) motion = Vec3.ZERO; // a respawn jump, not flight
        double radius = 0.4 * size;

        double rate = 0.25 * intensity * Math.sqrt(size);
        int count = (int) rate + (random.nextDouble() < rate - (int) rate ? 1 : 0);
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            Vec3 p = c.add(dir.scale(radius * (0.7 + random.nextDouble() * 0.4)));
            Vec3 v = dir.scale(0.01 + random.nextDouble() * 0.02).subtract(motion.scale(0.15)).add(0, 0.01, 0);
            if (isCold()) {
                if (random.nextInt(3) == 0) {
                    this.level().addParticle(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, v.x, v.y, v.z);
                } else {
                    this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, v.x * 0.5, v.y * 0.5, v.z * 0.5);
                }
            } else if (random.nextInt(3) == 0) {
                this.level().addParticle(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, v.x, v.y, v.z);
            } else {
                this.level().addParticle(ModParticles.EMBER.get(), p.x, p.y, p.z, v.x, v.y + 0.01, v.z);
            }
        }
        if (random.nextInt(6) == 0) {
            Vec3 p = motion.lengthSqr() < 1.0E-8 ? c : c.subtract(motion.normalize().scale(radius));
            this.level().addParticle(isCold() ? ModParticles.FROST_MIST.get() : ModParticles.HEAT_SMOKE.get(),
                    p.x, p.y, p.z, 0.0, 0.02, 0.0);
        }
    }
}
