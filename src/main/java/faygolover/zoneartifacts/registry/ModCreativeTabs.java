package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * {@code ForgeRegistries.CREATIVE_MODE_TABS} doesn't exist in 1.20.1 — creative tabs are a
 * vanilla registry, so this goes through vanilla's own {@code Registries.CREATIVE_MODE_TAB} key.
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZoneArtifacts.MODID);

    public static final RegistryObject<CreativeModeTab> ZONE_ARTIFACTS_TAB = TABS.register("zone_artifacts",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.fl_zone_arts.zone_artifacts"))
                    .icon(() -> new ItemStack(ModItems.ELECTRA_PLACER.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.ELECTRA_PLACER.get());
                        output.accept(ModItems.TESLA_PLACER.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
