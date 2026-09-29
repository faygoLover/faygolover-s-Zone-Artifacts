package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.entity.TeslaEntity;
import faygolover.zoneartifacts.entity.TeslaWaypointEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZoneArtifacts.MODID);

    public static final RegistryObject<EntityType<TeslaEntity>> TESLA = ENTITY_TYPES.register("tesla",
            () -> EntityType.Builder.<TeslaEntity>of(TeslaEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(48)
                    .updateInterval(2)
                    .fireImmune()
                    .build(new ResourceLocation(ZoneArtifacts.MODID, "tesla").toString()));

    /** A small, static, precisely-clickable stand-in for one Tesla route waypoint - see {@link
     *  TeslaWaypointEntity}'s own javadoc. {@code updateInterval(20)} is generous since she never
     *  moves once placed; there's simply nothing to resync more often than that. */
    public static final RegistryObject<EntityType<TeslaWaypointEntity>> TESLA_WAYPOINT = ENTITY_TYPES.register(
            "tesla_waypoint",
            () -> EntityType.Builder.<TeslaWaypointEntity>of(TeslaWaypointEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(48)
                    .updateInterval(20)
                    .fireImmune()
                    .build(new ResourceLocation(ZoneArtifacts.MODID, "tesla_waypoint").toString()));

    private ModEntities() {
    }
}
