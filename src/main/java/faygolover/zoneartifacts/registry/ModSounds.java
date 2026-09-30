package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Electra's custom sound cues. Registered here (rather than only listed in sounds.json) because
 * playing a sound in code needs a real {@link SoundEvent} from the registry — {@code sounds.json}
 * alone only tells the resource pack system which .ogg file(s) back an id, it doesn't create the
 * registry entry {@link net.minecraftforge.registries.ForgeRegistries#SOUND_EVENTS} looks up.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ZoneArtifacts.MODID);

    public static final RegistryObject<SoundEvent> ELECTRA_IDLE = register("electra_idle");
    public static final RegistryObject<SoundEvent> ELECTRA_BLAST_NUT = register("electra_blast_nut");
    public static final RegistryObject<SoundEvent> ELECTRA_BLAST_LIVING = register("electra_blast_living");
    public static final RegistryObject<SoundEvent> ELECTRA_HIT = register("electra_hit");
    public static final RegistryObject<SoundEvent> ELECTRA_HIT1 = register("electra_hit1");
    public static final RegistryObject<SoundEvent> TESLA_IDLE = register("tesla_idle");
    public static final RegistryObject<SoundEvent> ZHARKA_IDLE = register("zharka_idle");
    public static final RegistryObject<SoundEvent> INEY_IDLE = register("iney_idle");
    public static final RegistryObject<SoundEvent> INEY_ENTER = register("iney_enter");
    public static final RegistryObject<SoundEvent> COMET_IDLE = register("comet_idle");
    public static final RegistryObject<SoundEvent> COMET_EXPLODE = register("comet_explode");
    public static final RegistryObject<SoundEvent> RAZLOM_JET = register("razlom_jet");
    public static final RegistryObject<SoundEvent> PLESH_BLOWOUT = register("plesh_blowout");
    public static final RegistryObject<SoundEvent> VORONKA_BLOWOUT = register("voronka_blowout");
    public static final RegistryObject<SoundEvent> KARUSEL_BLOWOUT = register("karusel_blowout");
    public static final RegistryObject<SoundEvent> KARUSEL_IDLE = register("karusel_idle");
    public static final RegistryObject<SoundEvent> PLESH_IDLE = register("plesh_idle");
    public static final RegistryObject<SoundEvent> VORONKA_IDLE = register("voronka_idle");
    public static final RegistryObject<SoundEvent> PODUSHKA_BOUNCE = register("podushka_bounce");
    public static final RegistryObject<SoundEvent> BODY_TEAR = register("anomaly_body_tear");
    // 0.1.17–0.1.21: chemical anomalies, Gravi
    public static final RegistryObject<SoundEvent> GRAVY_HIT = register("gravy_hit");
    public static final RegistryObject<SoundEvent> CHEM_COMET_IDLE = register("chem_comet_idle");
    public static final RegistryObject<SoundEvent> CHEM_COMET_BURST = register("chem_comet_burst");
    public static final RegistryObject<SoundEvent> AMOEBA_GATHER = register("amoeba_gather");
    public static final RegistryObject<SoundEvent> AMOEBA_POP = register("amoeba_pop");
    public static final RegistryObject<SoundEvent> PUKH_PUFF = register("pukh_puff");
    public static final RegistryObject<SoundEvent> KISEL_IDLE = register("kisel_idle");
    public static final RegistryObject<SoundEvent> KISEL_HIT = register("kisel_hit");
    public static final RegistryObject<SoundEvent> FOG_IDLE = register("fog_idle");
    public static final RegistryObject<SoundEvent> FOG_JET = register("fog_jet");
    public static final RegistryObject<SoundEvent> DYMKA_DISTANT = register("dymka_distant");
    public static final RegistryObject<SoundEvent> SUMRAK_DRONE = register("sumrak_drone");
    public static final RegistryObject<SoundEvent> POPPY_HUM = register("poppy_hum");
    public static final RegistryObject<SoundEvent> RUST_BLAST = register("rust_blast");
    public static final RegistryObject<SoundEvent> RUST_HISS = register("rust_hiss");
    public static final RegistryObject<SoundEvent> RUST_CRACKLE = register("rust_crackle");
    public static final RegistryObject<SoundEvent> BUBBLE_POP = register("bubble_pop");
    public static final RegistryObject<SoundEvent> KHLOPUSHKA_CHARGE = register("khlopushka_charge");
    public static final RegistryObject<SoundEvent> KHLOPUSHKA_BANG = register("khlopushka_bang");
    public static final RegistryObject<SoundEvent> KAMERTON_RING = register("kamerton_ring");
    public static final RegistryObject<SoundEvent> WEB_SNAP = register("web_snap");
    public static final RegistryObject<SoundEvent> PSI_VOICES_L = register("psi_voices_l");
    public static final RegistryObject<SoundEvent> PSI_VOICES_R = register("psi_voices_r");
    public static final RegistryObject<SoundEvent> PSI_POLTER = register("psi_polter");
    public static final RegistryObject<SoundEvent> KAMERTON_CUT = register("kamerton_cut");
    public static final RegistryObject<SoundEvent> KAMERTON_SHATTER = register("kamerton_shatter");
    public static final RegistryObject<SoundEvent> TESLA_BLAST = register("tesla_blast");
    public static final RegistryObject<SoundEvent> ZHARKA_BLOW = register("zharka_blow");
    public static final RegistryObject<SoundEvent> RAZLOM_IDLE = register("razlom_idle");
    public static final RegistryObject<SoundEvent> GRAVI_IDLE = register("gravi_idle");
    public static final RegistryObject<SoundEvent> DYMKA_INSIDE = register("dymka_inside");
    public static final RegistryObject<SoundEvent> PLESH_HUM = register("plesh_hum");
    public static final RegistryObject<SoundEvent> KARUSEL_HUM = register("karusel_hum");

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(ZoneArtifacts.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {
    }
}
