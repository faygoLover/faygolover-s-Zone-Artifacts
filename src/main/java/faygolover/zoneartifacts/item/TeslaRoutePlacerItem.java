package faygolover.zoneartifacts.item;

import faygolover.zoneartifacts.tesla.RouteKind;
import faygolover.zoneartifacts.tesla.TeslaRouteService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Builds routes for a route anomaly — one item per {@link RouteKind} (Tesla, Comet); a placer only
 * sees and edits routes of its own kind. Right-click logic lives in {@link TeslaRouteService}: {@link #useOn} covers
 * clicks that land on a block, {@link #use} covers clicks into the air (a waypoint floating with
 * nothing behind it). Left-click only ever acts on waypoints and is detected client-side (see
 * {@code TeslaClientHandler}); this item never breaks blocks.
 */
public class TeslaRoutePlacerItem extends Item {

    private final RouteKind kind;

    public TeslaRoutePlacerItem(RouteKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public RouteKind kind() {
        return kind;
    }

    public static boolean isHeld(Player player) {
        return player.getMainHandItem().getItem() instanceof TeslaRoutePlacerItem
                || player.getOffhandItem().getItem() instanceof TeslaRoutePlacerItem;
    }

    /** Kind of the route placer in the main hand, else the off hand; null if neither holds one. */
    @Nullable
    public static RouteKind heldKind(Player player) {
        if (player.getMainHandItem().getItem() instanceof TeslaRoutePlacerItem placer) return placer.kind();
        if (player.getOffhandItem().getItem() instanceof TeslaRoutePlacerItem placer) return placer.kind();
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            // Where a block would go: the neighbour on the clicked face's side, or the clicked
            // block itself if it's replaceable (grass, a snow layer...).
            BlockPos placePos = new BlockPlaceContext(context).getClickedPos();
            TeslaRouteService.onUseOnBlock(player, kind, placePos, context.getClickLocation());
        }
        // Success on the client too, so it doesn't follow up with a second "use in air" packet.
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            TeslaRouteService.onUseInAir(serverPlayer, kind);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemTooltips.addDescription(getDescriptionId(), tooltip);
    }
}
