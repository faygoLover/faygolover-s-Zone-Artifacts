package faygolover.zoneartifacts.item;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Item descriptions from the lang files: lines {@code <key>.0}, {@code <key>.1}, ... are added in
 * gray until the next one is missing. {@link Language} (not the client-only I18n) so the call is
 * safe on either side.
 */
public final class ItemTooltips {

    private static final int MAX_LINES = 10;

    private ItemTooltips() {
    }

    /** The item's own description: {@code item.fl_zone_arts.<name>.desc.N}. */
    public static void addDescription(String descriptionId, List<Component> tooltip) {
        addLines(descriptionId + ".desc", tooltip);
    }

    public static void addLines(String keyPrefix, List<Component> tooltip) {
        Language language = Language.getInstance();
        for (int i = 0; i < MAX_LINES; i++) {
            String key = keyPrefix + "." + i;
            if (!language.has(key)) break;
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
