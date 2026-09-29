package faygolover.zoneartifacts;

import faygolover.zoneartifacts.config.ModClientConfig;
import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.network.ModNetwork;
import faygolover.zoneartifacts.registry.ModCreativeTabs;
import faygolover.zoneartifacts.registry.ModEntities;
import faygolover.zoneartifacts.registry.ModItems;
import faygolover.zoneartifacts.registry.ModParticles;
import faygolover.zoneartifacts.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Mod entry point.
 * <p>
 * Anomalies are configured in game, per anomaly, with the tuner items; global defaults and the
 * tuners' limits live in {@code config/fl_zone_arts-common.toml} ({@link ModCommonConfig}),
 * the per-player effect detail cap in {@code config/fl_zone_arts-client.toml} ({@link ModClientConfig}).
 */
@Mod(ZoneArtifacts.MODID)
public class ZoneArtifacts {

    public static final String MODID = "fl_zone_arts";

    public ZoneArtifacts() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModCommonConfig.SPEC, MODID + "-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ModClientConfig.SPEC, MODID + "-client.toml");

        ModNetwork.register();
    }
}
