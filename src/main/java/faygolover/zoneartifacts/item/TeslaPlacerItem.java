package faygolover.zoneartifacts.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Tesla's route-builder "spawner" item. Unlike {@link AnomalyPlacerItem}, all of its click
 * handling lives in {@code faygolover.zoneartifacts.tesla.TeslaInteractionHandler} rather than in
 * overridden item methods: building a multi-point route needs a full state machine driven by both
 * left- and right-clicks, and left-click has no per-item hook to override in the first place — so
 * both are caught as generic interaction events instead, exactly like {@code
 * AnomalyInteractionHandler} does for Electra's left-click-to-cycle-level.
 */
public class TeslaPlacerItem extends Item {

    public TeslaPlacerItem(Properties properties) {
        super(properties);
    }

    public static boolean isTeslaPlacer(ItemStack stack) {
        return stack.getItem() instanceof TeslaPlacerItem;
    }
}
