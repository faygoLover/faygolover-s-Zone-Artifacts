package faygolover.zoneartifacts.item;

import faygolover.zoneartifacts.tuner.TunerKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A tuner: left-click on an anomaly lowers one of its settings, right-click raises it, sneaking
 * uses the larger step. Aim anywhere into an Electra's zone, or at any point of a completed Tesla
 * route. Clicks are detected client-side ({@code TunerClientHandler}) and applied by the server
 * ({@code TunerService}); the item itself never places, uses or breaks anything.
 */
public class AnomalyTunerItem extends Item {

    private final TunerKind kind;

    public AnomalyTunerItem(TunerKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public TunerKind kind() {
        return kind;
    }

    public static boolean isHeld(Player player) {
        return player.getMainHandItem().getItem() instanceof AnomalyTunerItem
                || player.getOffhandItem().getItem() instanceof AnomalyTunerItem;
    }

    @Nullable
    public static TunerKind kindOf(ItemStack stack) {
        return stack.getItem() instanceof AnomalyTunerItem tuner ? tuner.kind() : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }
}
