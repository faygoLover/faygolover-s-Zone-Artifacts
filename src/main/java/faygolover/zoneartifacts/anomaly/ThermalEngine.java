package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DeadBushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Server-side logic of Zharka and Iney, called from {@link AnomalyEngine} every tick.
 * <ul>
 *     <li><b>Active</b> while any living, non-spectator, non-creative entity is inside the zone.
 *     The flag is synced to clients on every flip (visual/sound ramp-up).</li>
 *     <li><b>Damage</b> on one shared timer: the first pulse 0.2 s after activation, then every
 *     {@link AnomalyInstance#cooldownSeconds()} — everyone inside is hit at once. The timer resets
 *     when the zone empties.</li>
 *     <li><b>Zharka</b>: fire damage ({@code anomaly_heat}, counts as fire: fire resistance helps),
 *     each pulse may set the victim on fire. Thaws frozen entities.</li>
 *     <li><b>Iney</b>: cold damage ({@code anomaly_cold}), plus vanilla powder-snow freezing every
 *     tick (frost overlay, slowdown, shivering; leather armour protects from the freezing, not from
 *     the pulses). Puts out burning entities.</li>
 *     <li><b>Blocks</b> within zone + {@code thermal.blockRadius} change bit by bit while active
 *     (random sampling, so it takes a while and never happens all at once).</li>
 * </ul>
 */
public final class ThermalEngine {

    private ThermalEngine() {
    }

    public static void tick(ServerLevel level, AnomalyInstance instance) {
        boolean heat = AnomalyTypeIds.ZHARKA.equals(instance.typeId());
        AABB zone = AnomalyGeometry.zoneAabb(instance);
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, zone, ThermalEngine::activates);

        boolean active = !inside.isEmpty();
        if (active != instance.active()) {
            instance.setActive(active);
            AnomalySyncHandler.broadcastState(level, instance);
            if (active) {
                int interval = AnomalyDefaults.ticks(instance.cooldownSeconds());
                instance.setPulseTicks(Math.max(0, interval - Thermal.FIRST_PULSE_DELAY_TICKS));
                instance.setBlockTicks(0);
            }
        }
        if (!active) {
            instance.setPulseTicks(0);
            return;
        }

        // ---- continuous effects -------------------------------------------------------
        for (LivingEntity entity : inside) {
            if (heat) {
                if (entity.getTicksFrozen() > 0) entity.setTicksFrozen(0);
            } else {
                if (entity.isOnFire()) entity.clearFire();
                if (entity.canFreeze()) {
                    // LivingEntity.aiStep thaws 2 per tick outside powder snow, so the net gain is N - 2.
                    int max = entity.getTicksRequiredToFreeze();
                    entity.setTicksFrozen(Math.min(max, entity.getTicksFrozen() + ModCommonConfig.INEY_FREEZE_PER_TICK.get()));
                }
            }
        }

        // ---- shared damage pulse ------------------------------------------------------
        int interval = AnomalyDefaults.ticks(instance.cooldownSeconds());
        int pulse = instance.pulseTicks() + 1;
        if (pulse >= interval) {
            pulse = 0;
            pulse(level, instance, heat, inside);
        }
        instance.setPulseTicks(pulse);

        // ---- world ------------------------------------------------------------------
        int blockTicks = instance.blockTicks() + 1;
        if (blockTicks >= Thermal.BLOCK_INTERVAL_TICKS) {
            blockTicks = 0;
            boolean enabled = heat ? ModCommonConfig.ZHARKA_ALTERS_BLOCKS.get() : ModCommonConfig.INEY_ALTERS_BLOCKS.get();
            if (enabled) alterBlocks(level, zone, zone.inflate(ModCommonConfig.THERMAL_BLOCK_RADIUS.get()), heat);
        }
        instance.setBlockTicks(blockTicks);
    }

    private static boolean activates(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        // A GM building next to a Zharka in creative shouldn't set the place on fire.
        return !(entity instanceof Player player && player.isCreative());
    }

    private static void pulse(ServerLevel level, AnomalyInstance instance, boolean heat, List<LivingEntity> inside) {
        for (LivingEntity entity : inside) {
            if (!entity.isAlive()) continue;
            // Vanilla ignores most of a hit that lands within half a second of the previous one;
            // the pulse interval is set on purpose (possibly 0.1 s with low damage), so every pulse counts.
            entity.invulnerableTime = 0;
            AnomalyCombat.hurt(level, entity, heat ? Thermal.HEAT_DAMAGE_TYPE : Thermal.COLD_DAMAGE_TYPE, instance.damage(),
                    AnomalyGeometry.zoneAabb(instance).getCenter());
            if (heat && !entity.fireImmune() && level.random.nextDouble() < ModCommonConfig.ZHARKA_IGNITE_CHANCE.get()) {
                entity.setSecondsOnFire(ModCommonConfig.ZHARKA_IGNITE_SECONDS.get());
            }
        }
    }

    // ==== block transforms ==========================================================

    private static void alterBlocks(ServerLevel level, AABB zone, AABB box, boolean heat) {
        RandomSource random = level.random;
        int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY), minZ = (int) Math.floor(box.minZ);
        int sx = Math.max(1, (int) Math.ceil(box.maxX) - minX);
        int sy = Math.max(1, (int) Math.ceil(box.maxY) - minY);
        int sz = Math.max(1, (int) Math.ceil(box.maxZ) - minZ);
        long volume = (long) sx * sy * sz;
        int samples = (int) Math.min(Thermal.BLOCK_SAMPLES_MAX, Math.max(1, volume * Thermal.BLOCK_SAMPLES_PER_1000 / 1000));

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            cursor.set(minX + random.nextInt(sx), minY + random.nextInt(sy), minZ + random.nextInt(sz));
            if (!level.isLoaded(cursor) || level.isOutsideBuildHeight(cursor)) continue;
            BlockPos pos = cursor.immutable();
            BlockState state = level.getBlockState(pos);
            if (heat) heat(level, pos, state, random);
            else cold(level, pos, state, random, zone);
        }
    }

    // ---- Zharka: melts, evaporates, dries, bakes, ignites ----------------------------

    private static void heat(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        boolean nether = level.dimensionType().ultraWarm();

        // Ice melts one step at a time; the water then evaporates.
        if (state.is(Blocks.BLUE_ICE)) { set(level, pos, Blocks.PACKED_ICE.defaultBlockState()); return; }
        if (state.is(Blocks.PACKED_ICE)) { set(level, pos, Blocks.ICE.defaultBlockState()); return; }
        if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
            set(level, pos, nether ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState());
            return;
        }
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            set(level, pos, layers > 1 ? state.setValue(SnowLayerBlock.LAYERS, layers - 1) : Blocks.AIR.defaultBlockState());
            return;
        }
        if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            set(level, pos, Blocks.AIR.defaultBlockState());
            fizz(level, pos);
            return;
        }
        if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
            set(level, pos, Blocks.AIR.defaultBlockState());
            fizz(level, pos);
            return;
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            set(level, pos, state.setValue(BlockStateProperties.WATERLOGGED, false));
            fizz(level, pos);
            return;
        }

        // Soil dries out and scorches.
        if (state.is(Blocks.GRASS_BLOCK)) {
            BlockState path = Blocks.DIRT_PATH.defaultBlockState();
            set(level, pos, path.canSurvive(level, pos) ? path : Blocks.DIRT.defaultBlockState());
            return;
        }
        if (state.is(Blocks.FARMLAND)) { FarmBlock.turnToDirt(null, state, level, pos); return; }
        if (state.is(Blocks.MYCELIUM)) { set(level, pos, Blocks.DIRT.defaultBlockState()); return; }
        if (state.is(Blocks.PODZOL)) { set(level, pos, Blocks.COARSE_DIRT.defaultBlockState()); return; }
        if (state.is(Blocks.MUD)) { set(level, pos, Blocks.PACKED_MUD.defaultBlockState()); return; }
        if (state.is(Blocks.MUDDY_MANGROVE_ROOTS)) { set(level, pos, Blocks.MANGROVE_ROOTS.defaultBlockState()); return; }
        if (state.is(Blocks.WET_SPONGE)) { set(level, pos, Blocks.SPONGE.defaultBlockState()); fizz(level, pos); return; }
        if (state.is(Blocks.CLAY)) { set(level, pos, Blocks.TERRACOTTA.defaultBlockState()); return; }

        // Unlit campfires and candles light up.
        if (isCampfireOrCandle(state) && !state.getValue(BlockStateProperties.LIT)
                && !(state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))) {
            set(level, pos, state.setValue(BlockStateProperties.LIT, true));
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.6f, 1.0f);
            return;
        }

        // Anything flammable next to an empty spot catches fire (slowly: one chance in three).
        if (state.isAir() && random.nextInt(3) == 0 && touchesFlammable(level, pos)) {
            BlockState fire = BaseFireBlock.getState(level, pos);
            if (fire.canSurvive(level, pos)) set(level, pos, fire);
        }
    }

    private static boolean touchesFlammable(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (level.getBlockState(neighbour).isFlammable(level, neighbour, direction.getOpposite())) return true;
        }
        return false;
    }

    // ---- Iney: freezes water and lava, puts out fire, kills plants, snows -------------

    private static void cold(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, AABB zone) {
        if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
            set(level, pos, Blocks.ICE.defaultBlockState());
            return;
        }
        if (state.is(Blocks.LAVA)) {
            set(level, pos, state.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
            fizz(level, pos);
            return;
        }
        if (state.getBlock() instanceof BaseFireBlock) {
            set(level, pos, Blocks.AIR.defaultBlockState());
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.6f);
            return;
        }
        if (isCampfireOrCandle(state) && state.getValue(BlockStateProperties.LIT)) {
            set(level, pos, state.setValue(BlockStateProperties.LIT, false));
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.6f);
            return;
        }

        // Foliage and plants die.
        if (state.is(BlockTags.LEAVES)) {
            level.destroyBlock(pos, false);
            return;
        }
        Block block = state.getBlock();
        boolean dry = state.getFluidState().isEmpty(); // leave seagrass/kelp alone
        if (dry && (block instanceof CropBlock || block instanceof DoublePlantBlock
                || block instanceof VineBlock || block instanceof SugarCaneBlock)) {
            level.destroyBlock(pos, false);
            return;
        }
        if (dry && block instanceof BushBlock && !(block instanceof DeadBushBlock)) {
            BlockState dead = Blocks.DEAD_BUSH.defaultBlockState();
            if (dead.canSurvive(level, pos)) set(level, pos, dead);
            else level.destroyBlock(pos, false);
            return;
        }

        // Snow settles only inside the zone itself, on solid ground, and very slowly piles up
        // (up to 3 layers).
        if (!AnomalyGeometry.containsBlockCenter(zone, pos)) return;
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers < 3 && random.nextInt(Thermal.SNOW_GROW_ONE_IN) == 0) set(level, pos, state.setValue(SnowLayerBlock.LAYERS, layers + 1));
            return;
        }
        if (state.isAir() && random.nextInt(Thermal.SNOW_SETTLE_ONE_IN) == 0) {
            BlockState snow = Blocks.SNOW.defaultBlockState();
            if (snow.canSurvive(level, pos)) set(level, pos, snow);
        }
    }

    // ---- helpers --------------------------------------------------------------------

    private static boolean isCampfireOrCandle(BlockState state) {
        Block block = state.getBlock();
        return (block instanceof CampfireBlock || block instanceof AbstractCandleBlock)
                && state.hasProperty(BlockStateProperties.LIT);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
    }

    /** Hiss and a puff of steam (the vanilla "lava meets water" effect). */
    private static void fizz(ServerLevel level, BlockPos pos) {
        level.levelEvent(1501, pos, 0);
    }
}
