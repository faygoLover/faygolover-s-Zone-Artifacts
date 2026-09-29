package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/** Ids of the zone anomalies stored in {@link AnomalySavedData}. The Tesla is an entity and
 *  isn't listed here. */
public final class AnomalyTypeIds {

    public static final ResourceLocation ELECTRA = new ResourceLocation(ZoneArtifacts.MODID, "electra");

    private AnomalyTypeIds() {
    }
}
