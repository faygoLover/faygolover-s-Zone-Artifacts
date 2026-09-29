package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.entity.TeslaTypeIds;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import faygolover.zoneartifacts.item.TeslaRouteToolItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZoneArtifacts.MODID);

    public static final RegistryObject<Item> ELECTRA_PLACER = ITEMS.register("electra_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.ELECTRA, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TESLA_ROUTE_TOOL = ITEMS.register("tesla_route_tool",
            () -> new TeslaRouteToolItem(TeslaTypeIds.TESLA, new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
