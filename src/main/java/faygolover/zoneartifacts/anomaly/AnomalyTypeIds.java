package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/**
 * Known anomaly type ids. Items are registered well before any datapack loads, so a placer item
 * only needs the id it represents (as a plain ResourceLocation) — the actual {@link AnomalyType}
 * is looked up lazily from {@link AnomalyTypeManager} each time it's used.
 */
public final class AnomalyTypeIds {

    public static final ResourceLocation ELECTRA = new ResourceLocation(ZoneArtifacts.MODID, "electra");

    private AnomalyTypeIds() {
    }
}
