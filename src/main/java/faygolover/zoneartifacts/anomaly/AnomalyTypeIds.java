package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/** Ids of the zone anomalies stored in {@link AnomalySavedData}. The Tesla is an entity and
 *  isn't listed here. */
public final class AnomalyTypeIds {

    public static final ResourceLocation ELECTRA = new ResourceLocation(ZoneArtifacts.MODID, "electra");
    public static final ResourceLocation ZHARKA = new ResourceLocation(ZoneArtifacts.MODID, "zharka");
    public static final ResourceLocation INEY = new ResourceLocation(ZoneArtifacts.MODID, "iney");

    private AnomalyTypeIds() {
    }

    /** Passive-field anomalies: active while anyone is inside, damage on a shared interval. */
    public static boolean isThermal(ResourceLocation typeId) {
        return ZHARKA.equals(typeId) || INEY.equals(typeId);
    }
}
