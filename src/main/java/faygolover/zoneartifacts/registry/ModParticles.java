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
    /** Gravitational anomalies: dust drawn in, whirled and blown out. */
    public static final RegistryObject<SimpleParticleType> GRAV_DUST = PARTICLES.register("grav_dust", () -> new SimpleParticleType(false));
    /** Voronka / Karusel kills: drops of blood. */
    public static final RegistryObject<SimpleParticleType> BLOOD = PARTICLES.register("blood", () -> new SimpleParticleType(false));
    /** Chemical Comet: a heavy drop of its gas falling off; leaves a small stain where it lands. */
    /** Burning Fluff: a dark flake falling off its strands. */
    public static final RegistryObject<SimpleParticleType> PUKH_FLAKE = PARTICLES.register("pukh_flake", () -> new SimpleParticleType(false));
    /** Burning Fluff: a burning spore in its puffs. */
    public static final RegistryObject<SimpleParticleType> PUKH_SPORE = PARTICLES.register("pukh_spore", () -> new SimpleParticleType(false));
    /** Kisel: a glowing bubble rising in it and bursting. */
    public static final RegistryObject<SimpleParticleType> KISEL_BUBBLE = PARTICLES.register("kisel_bubble", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CHEM_DROP = PARTICLES.register("chem_drop", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> POPPY_PETAL = PARTICLES.register("poppy_petal", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
