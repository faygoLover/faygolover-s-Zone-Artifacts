package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZoneArtifacts.MODID);

    public static final RegistryObject<EntityType<TeslaEntity>> TESLA = ENTITIES.register("tesla",
            () -> EntityType.Builder.<TeslaEntity>of(TeslaEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.8f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .build("tesla"));

    private ModEntities() {
    }
}
