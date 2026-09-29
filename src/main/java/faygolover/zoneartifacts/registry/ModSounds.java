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
    public static final RegistryObject<SoundEvent> PODUSHKA_BOUNCE = register("podushka_bounce");
    public static final RegistryObject<SoundEvent> BODY_TEAR = register("anomaly_body_tear");

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(ZoneArtifacts.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {
    }
}
