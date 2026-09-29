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
    public static final ResourceLocation PLESH = new ResourceLocation(ZoneArtifacts.MODID, "plesh");
    public static final ResourceLocation VORONKA = new ResourceLocation(ZoneArtifacts.MODID, "voronka");
    public static final ResourceLocation KARUSEL = new ResourceLocation(ZoneArtifacts.MODID, "karusel");
    public static final ResourceLocation PODUSHKA = new ResourceLocation(ZoneArtifacts.MODID, "podushka");
    /** Always-on antigravity field; its own engine ({@link LiftEngine}). */
    public static final ResourceLocation LIFT = new ResourceLocation(ZoneArtifacts.MODID, "lift");
    /** A jelly puddle that lashes out with pseudopods; its own engine ({@link AmoebaEngine}). */
    public static final ResourceLocation AMOEBA = new ResourceLocation(ZoneArtifacts.MODID, "amoeba");

    private AnomalyTypeIds() {
    }

    /** Gravitational anomalies (Plesh, Voronka, Karusel, Podushka) — one engine, one client. */
    public static boolean isGravity(ResourceLocation typeId) {
        return PLESH.equals(typeId) || VORONKA.equals(typeId) || KARUSEL.equals(typeId) || PODUSHKA.equals(typeId);
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
