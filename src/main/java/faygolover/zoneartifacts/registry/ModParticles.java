package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The thermal anomalies' own particles (textures in {@code assets/fl_zone_arts/textures/particle},
 * sprite lists in {@code assets/fl_zone_arts/particles}). Registered on both sides; the look and
 * behaviour are client-only ({@code client.thermal.ThermalParticle}). They're only ever spawned
 * client-side, so they're never sent over the network.
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ZoneArtifacts.MODID);

    /** Zharka: small glowing spark. */
    public static final RegistryObject<SimpleParticleType> EMBER = PARTICLES.register("ember", () -> new SimpleParticleType(false));
    /** Zharka: small wisp of dark smoke. */
    public static final RegistryObject<SimpleParticleType> HEAT_SMOKE = PARTICLES.register("heat_smoke", () -> new SimpleParticleType(false));
    /** Iney: pale cold mist. */
    public static final RegistryObject<SimpleParticleType> FROST_MIST = PARTICLES.register("frost_mist", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
