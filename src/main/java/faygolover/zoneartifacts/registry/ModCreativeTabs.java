package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    // CreativeModeTab is a vanilla registry, not a Forge one, so it's registered via its
    // vanilla ResourceKey (Registries.CREATIVE_MODE_TAB), not ForgeRegistries.
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZoneArtifacts.MODID);

    public static final RegistryObject<CreativeModeTab> ANOMALIES_TAB = TABS.register("anomalies",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.fl_zone_arts.anomalies"))
                    .icon(() -> new ItemStack(ModItems.ELECTRA_PLACER.get()))
                    .displayItems((parameters, output) -> output.accept(ModItems.ELECTRA_PLACER.get()))
                    .build());

    private ModCreativeTabs() {
    }
}
