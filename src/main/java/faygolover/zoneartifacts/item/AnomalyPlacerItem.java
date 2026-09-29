package faygolover.zoneartifacts.item;

import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import faygolover.zoneartifacts.anomaly.AnomalyTargeting;
import faygolover.zoneartifacts.anomaly.AnomalyType;
import faygolover.zoneartifacts.anomaly.AnomalyTypeManager;
import faygolover.zoneartifacts.network.AnomalySyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The "spawner"/"clicker" item for one anomaly type: right-click places, removes or (via
 * {@link faygolover.zoneartifacts.anomaly.AnomalyInteractionHandler}, on left-click) cycles the
 * level of that anomaly type. Behaves like placing and interacting with an ordinary block:
 * <ul>
 *     <li>Right-click, aiming at an existing anomaly's zone (anywhere inside it, not just its
 *     anchor block) → removes it.</li>
 *     <li>Right-click, aiming at a plain block with no anomaly in reach → places a new one, one
 *     block level 1, adjacent to the clicked face (i.e. exactly where a normal block would land),
 *     never anchored inside the block that was actually clicked.</li>
 * </ul>
 * Both {@link #useOn} (fires when the vanilla block raytrace lands on a real block) and
 * {@link #use} (fires when it doesn't, e.g. aiming through open air into a zone that extends past
 * solid geometry) run the same "am I aiming at an anomaly?" check via {@link AnomalyTargeting} —
 * that check is a raw ray/AABB clip against the real, server-authoritative anomaly data, entirely
 * independent of which particular block vanilla's own raytrace happened to resolve to.
 */
public class AnomalyPlacerItem extends Item {

    private final ResourceLocation typeId;

    public AnomalyPlacerItem(ResourceLocation typeId, Properties properties) {
        super(properties);
        this.typeId = typeId;
    }

    public ResourceLocation typeId() {
        return typeId;
    }

    /** @return the anomaly type this stack places, or {@code null} if it isn't a placer item at all. */
    public static ResourceLocation typeIdOf(ItemStack stack) {
        return stack.getItem() instanceof AnomalyPlacerItem placer ? placer.typeId : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            Optional<AnomalyInstance> targeted = AnomalyTargeting.pick(serverLevel, player, typeId);
            if (targeted.isPresent()) {
                remove(serverLevel, targeted.get());
            } else {
                // Adjacent to the clicked face, exactly like placing any ordinary block — not
                // anchored inside the block that was actually clicked.
                BlockPos placeAt = context.getClickedPos().relative(context.getClickedFace());
                place(serverLevel, placeAt);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            AnomalyTargeting.pick(serverLevel, player, typeId)
                    .ifPresent(instance -> remove(serverLevel, instance));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void place(ServerLevel level, BlockPos pos) {
        AnomalyType type = AnomalyTypeManager.get(typeId);
        if (type == null) return; // no datapack currently defines this type — silently do nothing

        AnomalyInstance instance = new AnomalyInstance(typeId, pos, 1);
        AnomalySavedData.get(level).add(instance);
        AnomalySyncHandler.broadcastFullResync(level);
    }

    private void remove(ServerLevel level, AnomalyInstance instance) {
        AnomalySavedData.get(level).remove(instance);
        // A plain removal doesn't change any data the client still needs, so it's cheaper to send
        // a targeted delta than to resend the whole per-dimension list.
        AnomalySyncHandler.broadcastRemoval(level, instance);
    }
}
