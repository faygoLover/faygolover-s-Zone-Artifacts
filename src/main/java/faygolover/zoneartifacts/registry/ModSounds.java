package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ZoneArtifacts.MODID);

    public static final RegistryObject<SoundEvent> ELECTRA_IDLE = register("electra_idle");
    public static final RegistryObject<SoundEvent> ELECTRA_BLAST_NUT = register("electra_blast_nut");
    public static final RegistryObject<SoundEvent> ELECTRA_BLAST_LIVING = register("electra_blast_living");
    public static final RegistryObject<SoundEvent> ELECTRA_HIT = register("electra_hit");
    public static final RegistryObject<SoundEvent> ELECTRA_HIT1 = register("electra_hit1");
    public static final RegistryObject<SoundEvent> TESLA_IDLE = register("tesla_idle");

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(ZoneArtifacts.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {
    }
}
