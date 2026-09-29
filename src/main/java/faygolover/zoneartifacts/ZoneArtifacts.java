package faygolover.zoneartifacts;

import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.registry.ModCreativeTabs;
import faygolover.zoneartifacts.registry.ModEntities;
import faygolover.zoneartifacts.registry.ModItems;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Mod entry point.
 * <p>
 * Stage 1 scope: Electra (a static volumetric-zone anomaly) and Tesla (a roaming lightning-ball
 * entity that patrols a GM-built waypoint route). Both share as much of the same underlying
 * plumbing as makes sense — datapack-driven config records, the arc/lightning renderer, the
 * damage type — without forcing genuinely different mechanics (a fixed zone vs. a moving entity)
 * into one shape just for the sake of code reuse.
 */
@Mod(ZoneArtifacts.MODID)
public class ZoneArtifacts {

    public static final String MODID = "fl_zone_arts";

    public ZoneArtifacts() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);

        ModNetwork.register();
    }
}
