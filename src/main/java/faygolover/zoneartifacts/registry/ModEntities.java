package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.TeslaEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZoneArtifacts.MODID);

    /**
     * The visual ball is a full block; the hitbox is a bit smaller (0.8) so a route running right
     * along a floor or a wall doesn't register false collisions. Position updates every tick keep
     * the flight smooth on clients (plain entities don't interpolate on their own).
     */
    public static final RegistryObject<EntityType<TeslaEntity>> TESLA = ENTITY_TYPES.register("tesla",
            () -> EntityType.Builder.<TeslaEntity>of(TeslaEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.8f)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(new ResourceLocation(ZoneArtifacts.MODID, "tesla").toString()));

    private ModEntities() {
    }
}
