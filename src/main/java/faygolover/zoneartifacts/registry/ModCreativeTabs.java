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
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.ELECTRA_PLACER.get());
                        output.accept(ModItems.ZHARKA_PLACER.get());
                        output.accept(ModItems.INEY_PLACER.get());
                        output.accept(ModItems.RAZLOM_PLACER.get());
                        output.accept(ModItems.COLD_RAZLOM_PLACER.get());
                        output.accept(ModItems.PLESH_PLACER.get());
                        output.accept(ModItems.VORONKA_PLACER.get());
                        output.accept(ModItems.KARUSEL_PLACER.get());
                        output.accept(ModItems.PODUSHKA_PLACER.get());
                        output.accept(ModItems.TESLA_PLACER.get());
                        output.accept(ModItems.COMET_PLACER.get());
                        output.accept(ModItems.COLD_COMET_PLACER.get());
                        output.accept(ModItems.CHEM_COMET_PLACER.get());
                        output.accept(ModItems.GRAVI_PLACER.get());
                        output.accept(ModItems.LIFT_PLACER.get());
                        output.accept(ModItems.AMOEBA_PLACER.get());
                        output.accept(ModItems.KISEL_PLACER.get());
                        output.accept(ModItems.ACID_FOG_PLACER.get());
                        output.accept(ModItems.SWAMP_PLACER.get());
                        output.accept(ModItems.DYMKA_PLACER.get());
                        output.accept(ModItems.SUMRAK_PLACER.get());
                        output.accept(ModItems.PSI_PLACER.get());
                        output.accept(ModItems.POPPY_PLACER.get());
                        output.accept(ModItems.RUST_PLACER.get());
                        output.accept(ModItems.BUBBLES_PLACER.get());
                        output.accept(ModItems.KHLOPUSHKA_PLACER.get());
                        output.accept(ModItems.FIREFLY_PLACER.get());
                        output.accept(ModItems.KAMERTON_PLACER.get());
                        output.accept(ModItems.WEB_PLACER.get());
                        output.accept(ModItems.FANTOM_PLACER.get());
                        output.accept(ModItems.PUKH.get());
                        output.accept(ModItems.EZHIK.get());
                        output.accept(ModItems.PUKH_FLESH.get());
                        output.accept(ModItems.PDA.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
