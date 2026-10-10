package faygolover.zoneartifacts.gear;

import faygolover.zoneartifacts.item.ItemTooltips;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A bolt or a nut (new or rusty — only the look differs): thrown like a snowball, not as far
 * ({@link BoltEntity}). Anomalies take it for any thrown thing. Stacks to 64, each kind apart.
 */
public class BoltItem extends Item {

    /** How hard it's thrown (a snowball: 1.5). */
    public static final float THROW_POWER = 0.9f;

    public BoltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Under water it's thrown in silence (and the water slows it anyway).
        if (!player.isEyeInFluid(FluidTags.WATER)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BOLT_THROW.get(), SoundSource.PLAYERS,
                    0.6f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        }
        if (!level.isClientSide) {
            BoltEntity bolt = new BoltEntity(level, player);
            bolt.setItem(stack.copyWithCount(1));
            bolt.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, THROW_POWER, 1.0f);
            level.addFreshEntity(bolt);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(this, 4);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemTooltips.addLines("tooltip.fl_zone_arts.bolt", tooltip);
    }
}
