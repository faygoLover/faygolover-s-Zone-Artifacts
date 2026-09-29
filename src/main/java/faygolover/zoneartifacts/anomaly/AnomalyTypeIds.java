package faygolover.zoneartifacts.anomaly;

import faygolover.zoneartifacts.ZoneArtifacts;
import net.minecraft.resources.ResourceLocation;

/** Ids of the zone anomalies stored in {@link AnomalySavedData}. The Tesla is an entity and
 *  isn't listed here. */
public final class AnomalyTypeIds {

    public static final ResourceLocation ELECTRA = new ResourceLocation(ZoneArtifacts.MODID, "electra");
    public static final ResourceLocation ZHARKA = new ResourceLocation(ZoneArtifacts.MODID, "zharka");
    public static final ResourceLocation INEY = new ResourceLocation(ZoneArtifacts.MODID, "iney");
    public static final ResourceLocation RAZLOM = new ResourceLocation(ZoneArtifacts.MODID, "razlom");
    public static final ResourceLocation COLD_RAZLOM = new ResourceLocation(ZoneArtifacts.MODID, "cold_razlom");

    private AnomalyTypeIds() {
    }

    /** The Razlom and its soul-fire twin share one engine and one client. */
    public static boolean isRazlom(ResourceLocation typeId) {
        return RAZLOM.equals(typeId) || COLD_RAZLOM.equals(typeId);
    }

    /** Passive-field anomalies: active while anyone is inside, damage on a shared interval. */
    public static boolean isThermal(ResourceLocation typeId) {
        return ZHARKA.equals(typeId) || INEY.equals(typeId);
    }
}
