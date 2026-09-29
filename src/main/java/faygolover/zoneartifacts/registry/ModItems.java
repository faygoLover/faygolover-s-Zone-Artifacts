package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.anomaly.AnomalyTypeIds;
import faygolover.zoneartifacts.item.AnomalyPlacerItem;
import faygolover.zoneartifacts.item.AnomalyTunerItem;
import faygolover.zoneartifacts.item.PukhBlockItem;
import faygolover.zoneartifacts.item.TeslaRoutePlacerItem;
import faygolover.zoneartifacts.tesla.RouteKind;
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

    public static final RegistryObject<Item> RAZLOM_PLACER = ITEMS.register("razlom_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.RAZLOM, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> COLD_RAZLOM_PLACER = ITEMS.register("cold_razlom_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.COLD_RAZLOM, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PLESH_PLACER = ITEMS.register("plesh_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.PLESH, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> VORONKA_PLACER = ITEMS.register("voronka_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.VORONKA, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> KARUSEL_PLACER = ITEMS.register("karusel_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.KARUSEL, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PODUSHKA_PLACER = ITEMS.register("podushka_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.PODUSHKA, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TESLA_PLACER = ITEMS.register("tesla_placer",
            () -> new TeslaRoutePlacerItem(RouteKind.TESLA, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> COMET_PLACER = ITEMS.register("comet_placer",
            () -> new TeslaRoutePlacerItem(RouteKind.COMET, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> COLD_COMET_PLACER = ITEMS.register("cold_comet_placer",
            () -> new TeslaRoutePlacerItem(RouteKind.COLD_COMET, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> CHEM_COMET_PLACER = ITEMS.register("chem_comet_placer",
            () -> new TeslaRoutePlacerItem(RouteKind.CHEM_COMET, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> GRAVI_PLACER = ITEMS.register("gravi_placer",
            () -> new TeslaRoutePlacerItem(RouteKind.GRAVI, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> LIFT_PLACER = ITEMS.register("lift_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.LIFT, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> AMOEBA_PLACER = ITEMS.register("amoeba_placer",
            () -> new AnomalyPlacerItem(AnomalyTypeIds.AMOEBA, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PUKH = ITEMS.register("pukh",
            () -> new PukhBlockItem(ModBlocks.PUKH.get(), new Item.Properties()));

    /** A piece of Burning Fluff's fleshy base (what breaking it leaves). Nothing to do with it yet. */
    public static final RegistryObject<Item> PUKH_FLESH = ITEMS.register("pukh_flesh",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SIZE_TUNER = tuner("size_tuner", TunerKind.SIZE);
    public static final RegistryObject<Item> SPEED_TUNER = tuner("speed_tuner", TunerKind.SPEED);
    public static final RegistryObject<Item> COOLDOWN_TUNER = tuner("cooldown_tuner", TunerKind.COOLDOWN);
    public static final RegistryObject<Item> EFFECT_TUNER = tuner("effect_tuner", TunerKind.INTENSITY);
    public static final RegistryObject<Item> DAMAGE_TUNER = tuner("damage_tuner", TunerKind.DAMAGE);
    public static final RegistryObject<Item> TARGETING_TUNER = tuner("targeting_tuner", TunerKind.TARGETING);

    private static RegistryObject<Item> tuner(String name, TunerKind kind) {
        return ITEMS.register(name, () -> new AnomalyTunerItem(kind, new Item.Properties().stacksTo(1)));
    }

    private ModItems() {
    }
}
