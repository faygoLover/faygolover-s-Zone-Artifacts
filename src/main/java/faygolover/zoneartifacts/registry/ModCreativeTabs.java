package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(ForgeRegistries.CREATIVE_MODE_TABS, ZoneArtifacts.MODID);

    public static final RegistryObject<CreativeModeTab> ANOMALIES_TAB = TABS.register("anomalies",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.fl_zone_arts.anomalies"))
                    .icon(() -> new ItemStack(ModItems.ELECTRA_PLACER.get()))
                    .displayItems((parameters, output) -> output.accept(ModItems.ELECTRA_PLACER.get()))
                    .build());

    private ModCreativeTabs() {
    }
}
