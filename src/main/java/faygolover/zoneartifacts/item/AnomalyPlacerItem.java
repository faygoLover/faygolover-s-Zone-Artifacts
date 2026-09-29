package faygolover.zoneartifacts.item;

import faygolover.zoneartifacts.anomaly.AnomalyInstance;
import faygolover.zoneartifacts.anomaly.AnomalySavedData;
import faygolover.zoneartifacts.anomaly.AnomalyType;
import faygolover.zoneartifacts.anomaly.AnomalyTypeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * One instance of this item exists per anomaly type (see ModItems). Right-clicking a block:
 * <ul>
 *     <li>places a new anomaly of this type at level 1, if none of this type exists there yet;</li>
 *     <li>cycles the level (1 -&gt; 2 -&gt; ... -&gt; max -&gt; 1) if one of this type already
 *     sits at that exact block;</li>
 *     <li>shift + right-click removes an existing anomaly of this type at that position.</li>
 * </ul>
 * All logic runs server-side; the anomaly itself is data (see {@link AnomalySavedData}), not a
 * block or entity in the world.
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

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level instanceof ServerLevel serverLevel) {
            applyPlacement(serverLevel, player, pos);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void applyPlacement(ServerLevel serverLevel, @Nullable Player player, BlockPos pos) {
        AnomalyType type = AnomalyTypeManager.get(anomalyTypeId);
        if (type == null) {
            notify(player, "anomaly type '" + anomalyTypeId + "' is not loaded (check the datapack / run /reload)");
            return;
        }

        AnomalySavedData data = AnomalySavedData.get(serverLevel);
        Optional<AnomalyInstance> existing = findAt(data, pos, anomalyTypeId);

        if (player != null && player.isShiftKeyDown()) {
            if (existing.isPresent()) {
                data.remove(existing.get());
                notify(player, "removed " + anomalyTypeId + " at " + pos.toShortString());
            } else {
                notify(player, "nothing to remove at " + pos.toShortString());
            }
            return;
        }

        if (existing.isPresent()) {
            AnomalyInstance instance = existing.get();
            int nextLevel = instance.level() % type.maxLevel() + 1;
            instance.setLevel(nextLevel);
            data.setDirty();
            notify(player, anomalyTypeId + " at " + pos.toShortString() + " -> level " + nextLevel);
        } else {
            data.add(new AnomalyInstance(anomalyTypeId, pos.immutable(), 1));
            notify(player, "placed " + anomalyTypeId + " at " + pos.toShortString() + " (level 1)");
        }
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
