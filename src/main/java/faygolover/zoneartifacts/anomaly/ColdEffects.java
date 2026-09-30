package faygolover.zoneartifacts.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * What the soul-fire (cold) anomalies do instead of setting things on fire: freeze.
 * <ul>
 *     <li>Entities: vanilla freezing (frost on the screen, shivering; a fully frozen player keeps
 *     taking the vanilla freezing damage) for the given time, plus Slowness II; burning stops.
 *     Leather armour keeps off the freezing, not the slowness.</li>
 *     <li>Blocks: water freezes, lava turns to obsidian, fires and campfires/candles go out, and
 *     on open ground a layer of snow settles.</li>
 * </ul>
 */
public final class ColdEffects {

    private ColdEffects() {
    }

    /** Freezes an entity for {@code seconds}. Creative players and spectators are left alone. */
    public static void freeze(Entity entity, int seconds) {
        if (seconds <= 0 || AnomalyCombat.spectatorExempt(entity)) return;
        if (AnomalyCombat.creativeExempt(entity)) return;
        entity.clearFire();
        if (!(entity instanceof LivingEntity living)) return;
        if (living.canFreeze()) {
            // Fully frozen, and it stays that way for `seconds` (vanilla thaws 2 per tick).
            int frozen = living.getTicksRequiredToFreeze() + seconds * 40;
            if (living.getTicksFrozen() < frozen) living.setTicksFrozen(frozen);
        }
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, seconds * 20, 1));
    }

    /**
     * Chills one spot: water → ice, lava → obsidian, fire out, campfire/candle out, or a snow layer
     * on open ground. Never breaks anything.
     *
     * @return whether the spot changed
     */
    public static boolean chill(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        // Water and lava only turn solid when nobody is in them (never seal a creature in).
        if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
            BlockState ice = Blocks.ICE.defaultBlockState();
            if (!level.isUnobstructed(ice, pos, CollisionContext.empty())) return false;
            level.setBlockAndUpdate(pos, ice);
            return true;
        }
        if (state.is(Blocks.LAVA) && state.getFluidState().isSource()) {
            BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
            if (!level.isUnobstructed(obsidian, pos, CollisionContext.empty())) return false;
            level.setBlockAndUpdate(pos, obsidian);
            level.levelEvent(1501, pos, 0);
            return true;
        }
        if (state.getBlock() instanceof BaseFireBlock) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.6f);
            return true;
        }
        if ((state.getBlock() instanceof CampfireBlock || state.getBlock() instanceof AbstractCandleBlock)
                && state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
            level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, false));
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.6f);
            return true;
        }
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers < 3) {
                level.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers + 1));
                return true;
            }
            return false;
        }
        if (state.isAir()) {
            BlockState snow = Blocks.SNOW.defaultBlockState();
            if (snow.canSurvive(level, pos)) {
                level.setBlockAndUpdate(pos, snow);
                return true;
            }
        }
        return false;
    }
}
