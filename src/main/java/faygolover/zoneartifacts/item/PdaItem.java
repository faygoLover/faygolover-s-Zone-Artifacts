package faygolover.zoneartifacts.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The anomaly KPK: used on an anomaly (a zone, a route point, Burning Fluff, a Hedgehog, a Web
 * thread) it opens its settings window — sliders, switches, moving it. Clicks are handled on the
 * client ({@code client.pda.PdaClientHandler}), which asks the server for the settings.
 */
public class PdaItem extends Item {

    public PdaItem(Properties properties) {
        super(properties);
    }

    public static boolean holds(Player player) {
        return player.getMainHandItem().getItem() instanceof PdaItem || player.getOffhandItem().getItem() instanceof PdaItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemTooltips.addDescription(getDescriptionId(), tooltip);
    }
}
