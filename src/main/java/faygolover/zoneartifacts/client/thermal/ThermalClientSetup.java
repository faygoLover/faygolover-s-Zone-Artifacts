package faygolover.zoneartifacts.client.thermal;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.client.gravity.BloodParticle;
import faygolover.zoneartifacts.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hooks the thermal particles' look ({@link ThermalParticle}) to their registered types. */
@Mod.EventBusSubscriber(modid = ZoneArtifacts.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ThermalClientSetup {

    private ThermalClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.EMBER.get(), sprites -> new ThermalParticle.Provider(sprites, ThermalParticle.Kind.EMBER));
        event.registerSpriteSet(ModParticles.HEAT_SMOKE.get(), sprites -> new ThermalParticle.Provider(sprites, ThermalParticle.Kind.HEAT_SMOKE));
        event.registerSpriteSet(ModParticles.FROST_MIST.get(), sprites -> new ThermalParticle.Provider(sprites, ThermalParticle.Kind.FROST_MIST));
        event.registerSpriteSet(ModParticles.GRAV_DUST.get(), sprites -> new ThermalParticle.Provider(sprites, ThermalParticle.Kind.DUST));
        event.registerSpriteSet(ModParticles.BLOOD.get(), BloodParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CHEM_DROP.get(), faygolover.zoneartifacts.client.chem.ChemDropParticle.Provider::new);
        event.registerSpriteSet(ModParticles.KISEL_BUBBLE.get(), faygolover.zoneartifacts.client.kisel.KiselBubbleParticle.Provider::new);
        event.registerSpriteSet(ModParticles.PUKH_FLAKE.get(), sprites -> new faygolover.zoneartifacts.client.pukh.PukhParticle.Provider(sprites, false));
        event.registerSpriteSet(ModParticles.PUKH_SPORE.get(), sprites -> new faygolover.zoneartifacts.client.pukh.PukhParticle.Provider(sprites, true));
    }
}
