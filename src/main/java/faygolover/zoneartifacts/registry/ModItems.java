package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import faygolover.zoneartifacts.item.AnomalyTunerItem;
import faygolover.zoneartifacts.item.TeslaRoutePlacerItem;
import faygolover.zoneartifacts.tuner.TunerKind;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZoneArtifacts.MODID);

    public static final RegistryObject<Item> ELECTRA_PLACER = ITEMS.register("electra_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.ELECTRA, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ZHARKA_PLACER = ITEMS.register("zharka_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.ZHARKA, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> INEY_PLACER = ITEMS.register("iney_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.INEY, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TESLA_PLACER = ITEMS.register("tesla_placer",
            () -> new TeslaRoutePlacerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SIZE_TUNER = tuner("size_tuner", TunerKind.SIZE);
    public static final RegistryObject<Item> SPEED_TUNER = tuner("speed_tuner", TunerKind.SPEED);
    public static final RegistryObject<Item> COOLDOWN_TUNER = tuner("cooldown_tuner", TunerKind.COOLDOWN);
    public static final RegistryObject<Item> EFFECT_TUNER = tuner("effect_tuner", TunerKind.INTENSITY);
    public static final RegistryObject<Item> DAMAGE_TUNER = tuner("damage_tuner", TunerKind.DAMAGE);

    private static RegistryObject<Item> tuner(String name, TunerKind kind) {
        return ITEMS.register(name, () -> new AnomalyTunerItem(kind, new Item.Properties().stacksTo(1)));
    }

    private ModItems() {
    }
}
