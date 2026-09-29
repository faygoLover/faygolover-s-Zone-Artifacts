package faygolover.zoneartifacts;

import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.registry.ModCreativeTabs;
import faygolover.zoneartifacts.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Mod entry point.
 * <p>
 * Stage 1 scope: only the Electra anomaly, but the underlying "volumetric zone"
 * engine (see the {@code faygolover.zoneartifacts.anomaly} package) is written to be
 * shared by future anomalies of the same family (Zharka, Iney, Lift, Voronka, ...)
 * purely through new datapack files, with no new Java code required for those.
 */
@Mod(ZoneArtifacts.MODID)
public class ZoneArtifacts {

    public static final String MODID = "fl_zone_arts";

    public ZoneArtifacts() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        ModNetwork.register();
    }
}
