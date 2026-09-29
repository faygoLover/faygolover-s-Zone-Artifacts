package faygolover.zoneartifacts.item;

import faygolover.zoneartifacts.tesla.TeslaRouteService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds Tesla routes. Right-click logic lives in {@link TeslaRouteService}: {@link #useOn} covers
 * clicks that land on a block, {@link #use} covers clicks into the air (a waypoint floating with
 * nothing behind it). Left-click only ever acts on waypoints and is detected client-side (see
 * {@code TeslaClientHandler}); this item never breaks blocks.
 */
public class TeslaRoutePlacerItem extends Item {

    public TeslaRoutePlacerItem(Properties properties) {
        super(properties);
    }

    public static boolean isHeld(Player player) {
        return player.getMainHandItem().getItem() instanceof TeslaRoutePlacerItem
                || player.getOffhandItem().getItem() instanceof TeslaRoutePlacerItem;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            // Where a block would go: the neighbour on the clicked face's side, or the clicked
            // block itself if it's replaceable (grass, a snow layer...).
            BlockPos placePos = new BlockPlaceContext(context).getClickedPos();
            TeslaRouteService.onUseOnBlock(player, placePos, context.getClickLocation());
        }
        // Success on the client too, so it doesn't follow up with a second "use in air" packet.
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            TeslaRouteService.onUseInAir(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }
}
