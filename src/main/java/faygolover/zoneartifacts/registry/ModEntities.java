package faygolover.zoneartifacts.registry;

import faygolover.zoneartifacts.ZoneArtifacts;
import faygolover.zoneartifacts.tesla.ChemCometEntity;
import faygolover.zoneartifacts.tesla.ColdCometEntity;
import faygolover.zoneartifacts.tesla.Gravi;
import faygolover.zoneartifacts.tesla.GraviEntity;
import faygolover.zoneartifacts.tesla.CometEntity;
import faygolover.zoneartifacts.tesla.Tesla;
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
     * Base size (size 1): the visual ball is a full block, the hitbox a bit smaller so a route
     * running right along a floor or a wall doesn't register false collisions. The entity scales
     * both with its tuned size. Position updates every tick keep the flight smooth on clients
     * (plain entities don't interpolate on their own).
     */
    public static final RegistryObject<EntityType<TeslaEntity>> TESLA = ENTITY_TYPES.register("tesla",
            () -> EntityType.Builder.<TeslaEntity>of(TeslaEntity::new, MobCategory.MISC)
                    .sized(Tesla.BASE_HITBOX, Tesla.BASE_HITBOX)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(new ResourceLocation(ZoneArtifacts.MODID, "tesla").toString()));

    /** The Comet: same body and tracking as the Tesla (it extends it), its own renderer. */
    public static final RegistryObject<EntityType<CometEntity>> COMET = ENTITY_TYPES.register("comet",
            () -> EntityType.Builder.<CometEntity>of(CometEntity::new, MobCategory.MISC)
                    .sized(Tesla.BASE_HITBOX, Tesla.BASE_HITBOX)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(new ResourceLocation(ZoneArtifacts.MODID, "comet").toString()));

    /** The Cold Comet (soul fire), drawn by the same renderer in cold colours. */
    public static final RegistryObject<EntityType<ColdCometEntity>> COLD_COMET = ENTITY_TYPES.register("cold_comet",
            () -> EntityType.Builder.<ColdCometEntity>of(ColdCometEntity::new, MobCategory.MISC)
                    .sized(Tesla.BASE_HITBOX, Tesla.BASE_HITBOX)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(new ResourceLocation(ZoneArtifacts.MODID, "cold_comet").toString()));

    /** The Chemical Comet: same body and tracking as the Tesla, drawn as gas. */
    public static final RegistryObject<EntityType<ChemCometEntity>> CHEM_COMET = ENTITY_TYPES.register("chem_comet",
            () -> EntityType.Builder.<ChemCometEntity>of(ChemCometEntity::new, MobCategory.MISC)
                    .sized(Tesla.BASE_HITBOX, Tesla.BASE_HITBOX)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(new ResourceLocation(ZoneArtifacts.MODID, "chem_comet").toString()));

    /** Gravi: invisible, a quarter-block hitbox whatever its size, passes through everything. */
    public static final RegistryObject<EntityType<GraviEntity>> GRAVI = ENTITY_TYPES.register("gravi",
            () -> EntityType.Builder.<GraviEntity>of(GraviEntity::new, MobCategory.MISC)
                    .sized(Gravi.HITBOX, Gravi.HITBOX)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(new ResourceLocation(ZoneArtifacts.MODID, "gravi").toString()));

    private ModEntities() {
    }
}
