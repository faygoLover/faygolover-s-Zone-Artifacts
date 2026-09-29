package faygolover.zoneartifacts.entity;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/**
 * Known Tesla type ids — {@code AnomalyTypeIds}' counterpart. The route tool item is registered
 * well before any datapack loads, so it only needs the id it represents as a plain
 * {@link ResourceLocation}; the actual {@link TeslaType} is looked up lazily from
 * {@link TeslaTypeManager} each time it's needed.
 */
public final class TeslaTypeIds {

    public static final ResourceLocation TESLA = new ResourceLocation(ZoneArtifacts.MODID, "tesla");

    private TeslaTypeIds() {
    }
}
