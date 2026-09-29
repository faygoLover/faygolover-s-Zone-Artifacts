package faygolover.zoneartifacts.item;

import faygolover.zoneartifacts.anomaly.AnomalyDefaults;
import faygolover.zoneartifacts.anomaly.AnomalyGeometry;
import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One instance of this item exists per zone anomaly type (see ModItems).
 * <ul>
 *     <li>Right-click on a block places a new anomaly with the default settings, next to the
 *     clicked face like a block. Never into or onto another anomaly: aiming into an existing zone
 *     does nothing ({@code AnomalyInteractionHandler}), and a spot inside a zone is refused here.</li>
 *     <li>Left-click anywhere in an existing zone removes it ({@code ClientAnomalyInputHandler} +
 *     {@code RemoveAnomalyPacket}).</li>
 * </ul>
 * Size, cooldown, damage and effect intensity are changed with the tuner items.
 */
public class AnomalyPlacerItem extends Item {

    private final ResourceLocation anomalyTypeId;

    public AnomalyPlacerItem(ResourceLocation anomalyTypeId, Properties properties) {
        super(properties);
        this.anomalyTypeId = anomalyTypeId;
    }

    public ResourceLocation anomalyTypeId() {
        return anomalyTypeId;
    }

    @Nullable
    public static ResourceLocation heldTypeId(Player player) {
        ResourceLocation main = fromStack(player.getMainHandItem());
        if (main != null) return main;
        return fromStack(player.getOffhandItem());
    }

    @Nullable
    private static ResourceLocation fromStack(ItemStack stack) {
        return stack.getItem() instanceof AnomalyPlacerItem placer ? placer.anomalyTypeId() : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        // Placed like an ordinary block: into the neighbour on the clicked face's side, or into
        // the clicked block itself if that one is replaceable (grass, a snow layer...).
        // The swamp goes into the clicked block itself (its top face becomes the surface).
        BlockPos pos = AnomalyTypeIds.SWAMP.equals(anomalyTypeId) ? context.getClickedPos()
                : new BlockPlaceContext(context).getClickedPos();
        Player player = context.getPlayer();

        if (level instanceof ServerLevel serverLevel) {
            place(serverLevel, player, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void place(ServerLevel serverLevel, @Nullable Player player, BlockPos pos) {
        AnomalySavedData data = AnomalySavedData.get(serverLevel);
        for (AnomalyInstance existing : List.copyOf(data.instances())) {
            if (existing.pos().equals(pos) || AnomalyGeometry.containsBlockCenter(AnomalyGeometry.zoneAabb(existing), pos)) {
                notify(player, Component.translatable("message.fl_zone_arts.anomaly.inside_other"));
                return;
            }
        }

        AnomalyInstance instance = AnomalyInstance.create(anomalyTypeId, pos);
        if (player != null) instance.setYaw(player.getYRot());
        data.add(instance);
        notify(player, Component.translatable("message.fl_zone_arts.anomaly.placed",
                Component.translatable(AnomalyDefaults.nameKey(anomalyTypeId)), pos.toShortString()));
        AnomalySyncHandler.broadcast(serverLevel);
    }

    private static void notify(@Nullable Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemTooltips.addDescription(getDescriptionId(), tooltip);
    }
}
