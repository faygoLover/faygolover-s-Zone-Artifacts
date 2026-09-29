package faygolover.zoneartifacts.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The GM tool for laying out a Tesla's patrol route: right-click a chain of blocks, then
 * right-click the first one again to close it into a loop (see {@code
 * TeslaRouteInteractionHandler} for the actual click logic this item is built around). One
 * instance exists per Tesla type, exactly like {@code AnomalyPlacerItem} does for Electra.
 * <p>
 * The chain-in-progress is stored in the held stack's own NBT, not some separate per-player state,
 * so it naturally survives the GM opening their inventory, switching hotbar slots, or logging off
 * — the same trick a vanilla lodestone compass uses to remember its target.
 */
public class TeslaRouteToolItem extends Item {

    private static final String TAG_CHAIN = "fl_zone_arts_chain";

    private final ResourceLocation teslaTypeId;

    public TeslaRouteToolItem(ResourceLocation teslaTypeId, Properties properties) {
        super(properties);
        this.teslaTypeId = teslaTypeId;
    }

    public ResourceLocation teslaTypeId() {
        return teslaTypeId;
    }

    /** The main- or off-hand stack holding one of these, or {@code null} if neither hand does —
     *  mirrors {@code AnomalyPlacerItem#heldTypeId}. */
    @Nullable
    public static ItemStack heldStack(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof TeslaRouteToolItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof TeslaRouteToolItem) return off;
        return null;
    }

    public static List<BlockPos> getChain(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_CHAIN)) return List.of();
        ListTag list = tag.getList(TAG_CHAIN, Tag.TAG_LONG);
        List<BlockPos> chain = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            chain.add(BlockPos.of(list.getLong(i)));
        }
        return chain;
    }

    public static void setChain(ItemStack stack, List<BlockPos> chain) {
        ListTag list = new ListTag();
        for (BlockPos pos : chain) {
            list.add(LongTag.valueOf(pos.asLong()));
        }
        stack.getOrCreateTag().put(TAG_CHAIN, list);
    }

    public static void clearChain(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_CHAIN);
        }
    }
}
