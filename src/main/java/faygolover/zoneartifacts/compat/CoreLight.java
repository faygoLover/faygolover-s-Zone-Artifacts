package faygolover.zoneartifacts.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

/**
 * RP Core (optional): the family-wide answer to "does this entity carry a switched-on light". Every RP mod that
 * knows about lights (RP Lights, RP Psyche's item list) registers there, so a new lantern is seen here without
 * touching this mod's config. Without RP Core the answer is "no" and only our own {@code web_light_items} list counts.
 * RP Core classes are touched only inside {@link Impl}, which loads only when the mod is present.
 */
public final class CoreLight {
    private CoreLight() {}

    private static final boolean LOADED = ModList.get().isLoaded("rpcore");

    public static boolean carried(LivingEntity entity) {
        return LOADED && Impl.carried(entity);
    }

    private static final class Impl {
        static boolean carried(LivingEntity entity) {
            return faygolover.rpcore.api.RpCoreAPI.carriesLight(entity);
        }
    }
}
