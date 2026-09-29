package faygolover.zoneartifacts.item;

import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import faygolover.zoneartifacts.anomaly.AnomalyType;
import faygolover.zoneartifacts.anomaly.AnomalyTypeManager;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * One instance of this item exists per anomaly type (see ModItems).
 * <p>
 * Interacting with an <em>existing</em> anomaly of this type (aiming anywhere inside its zone,
 * like clicking a light block) is handled elsewhere and cancels the vanilla interaction before
 * {@link #useOn} ever runs: right-click level-cycling goes through {@code AnomalyInteractionHandler}
 * (a server-side raytrace), left-click removal goes through {@code ClientAnomalyInputHandler}
 * + {@code RemoveAnomalyPacket} (has to start client-side — see that class's javadoc for why).
 * <p>
 * This class's {@link #useOn} is therefore just the fallback: right-click on a block with no
 * anomaly of this type on it yet places a new one at level 1.
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
        // BlockPlaceContext already resolves exactly that, the same way vanilla block placement does.
        BlockPos pos = new BlockPlaceContext(context).getClickedPos();
        Player player = context.getPlayer();

        if (level instanceof ServerLevel serverLevel) {
            placeIfEmpty(serverLevel, player, pos);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void placeIfEmpty(ServerLevel serverLevel, @Nullable Player player, BlockPos pos) {
        AnomalyType type = AnomalyTypeManager.get(anomalyTypeId);
        if (type == null) {
            notify(player, "anomaly type '" + anomalyTypeId + "' is not loaded (check the datapack / run /reload)");
            return;
        }

        AnomalySavedData data = AnomalySavedData.get(serverLevel);
        if (findAt(data, pos, anomalyTypeId).isPresent()) {
            // An anomaly is already anchored exactly here; AnomalyInteractionHandler's raytrace
            // should have caught the click and cycled its level before this ever runs. If it
            // somehow didn't (e.g. clicking exactly on the block from an odd angle), do nothing
            // rather than silently stacking a second one on top.
            return;
        }

        data.add(new AnomalyInstance(anomalyTypeId, pos.immutable(), 1));
        notify(player, "placed " + anomalyTypeId + " at " + pos.toShortString() + " (level 1)");
        AnomalySyncHandler.broadcast(serverLevel);
    }

    private static Optional<AnomalyInstance> findAt(AnomalySavedData data, BlockPos pos, ResourceLocation typeId) {
        for (AnomalyInstance instance : List.copyOf(data.instances())) {
            if (instance.pos().equals(pos) && instance.typeId().equals(typeId)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    private static void notify(@Nullable Player player, String message) {
        if (player != null) {
            player.displayClientMessage(Component.literal("fl_zone_arts: " + message), true);
        }
    }
}
