package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.ChemComet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lingering chemical clouds (the Chemical Comet's burst). Server-side only the harm: a cloud
 * spreads from the burst over the first second and a half to its full radius, hugs the ground
 * {@link ChemComet#CLOUD_HEIGHT} high, and every half second burns whoever is inside with chemical
 * damage (they run out of it). The look is the client's ({@code client.chem.ChemClient}).
 * Clouds aren't saved: a restart clears the air.
 */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID)
public final class ChemClouds {

    private static final int PULSE_TICKS = 10;
    private static final int SPREAD_TICKS = 30;
    /** Blocks changed by one burst at most. */
    private static final int MAX_WITHERED = 400;

    private record Cloud(ResourceKey<Level> dimension, Vec3 ground, double radius, long born, long dies, float damage) {
        double radiusAt(long now) {
            double t = Mth.clamp((now - born) / (double) SPREAD_TICKS, 0.0, 1.0);
            return radius * (0.35 + 0.65 * (1.0 - (1.0 - t) * (1.0 - t)));
        }
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();

    private ChemClouds() {
    }

    public static void add(ServerLevel level, Vec3 ground, double radius, double seconds, float damage) {
        long now = level.getGameTime();
        CLOUDS.add(new Cloud(level.dimension(), ground, radius, now, now + AnomalyDefaults.ticks(seconds), damage));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || CLOUDS.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
            Cloud cloud = it.next();
            if (!cloud.dimension().equals(level.dimension())) continue;
            if (now >= cloud.dies()) {
                it.remove();
                continue;
            }
            if ((now - cloud.born()) % PULSE_TICKS != 0 || cloud.damage() <= 0.0f) continue;
            double r = cloud.radiusAt(now);
            Vec3 g = cloud.ground();
            AABB box = new AABB(g.x - r, g.y - 0.5, g.z - r, g.x + r, g.y + ChemComet.CLOUD_HEIGHT, g.z + r);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, ChemClouds::hurtable)) {
                double dx = living.getX() - g.x;
                double dz = living.getZ() - g.z;
                if (dx * dx + dz * dz > r * r) continue;
                living.invulnerableTime = 0;
                AnomalyCombat.hurt(level, living, ChemComet.DAMAGE_TYPE, cloud.damage(), g);
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

    /**
     * Everything living dies within {@code radius} (surely near the middle, now and then at the
     * edge): grass, mycelium, podzol and moss turn to dirt; flowers, grass, crops, saplings,
     * mushrooms, vines and moss carpets vanish; leaves fall. Nothing under water.
     */
    public static void wither(ServerLevel level, Vec3 c, double radius, RandomSource random) {
        BlockPos origin = BlockPos.containing(c);
        int r = Mth.ceil(radius);
        int changed = 0;
        for (int dx = -r; dx <= r && changed < MAX_WITHERED; dx++) {
            for (int dy = -r; dy <= r && changed < MAX_WITHERED; dy++) {
                for (int dz = -r; dz <= r && changed < MAX_WITHERED; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > radius || !level.isLoaded(pos)) continue;
                    if (random.nextDouble() > 1.0 - 0.6 * (d / radius)) continue;
                    if (witherOne(level, pos, random)) changed++;
                }
            }
        }
    }

    private static boolean witherOne(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL) || state.is(Blocks.MOSS_BLOCK)) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            return true;
        }
        boolean plant = state.getBlock() instanceof BushBlock || state.getBlock() instanceof VineBlock
                || state.is(Blocks.MOSS_CARPET) || state.is(BlockTags.LEAVES);
        if (!plant) return false;
        if (random.nextInt(3) == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.02);
        }
        // Tall plants: removing one half takes the other with it.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        return true;
    }
}
