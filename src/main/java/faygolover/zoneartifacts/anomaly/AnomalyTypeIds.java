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
    /** Glowing, bubbling acid puddle ({@link KiselEngine}). */
    public static final ResourceLocation KISEL = new ResourceLocation(ZoneArtifacts.MODID, "kisel");
    /** Hovering acid haze with jets of vapour ({@link AcidFogEngine}). */
    public static final ResourceLocation ACID_FOG = new ResourceLocation(ZoneArtifacts.MODID, "acid_fog");

    /** Ground that swallows whoever walks out onto it ({@link SwampEngine}, {@link SwampPhysics}). */
    public static final ResourceLocation SWAMP = new ResourceLocation(ZoneArtifacts.MODID, "tryasina");
    /** Haze: fog that closes in on whoever is inside and muffles sound (client only). */
    public static final ResourceLocation DYMKA = new ResourceLocation(ZoneArtifacts.MODID, "dymka");
    /** Dusk: churning darkness that swallows light and sound (client only). */
    public static final ResourceLocation SUMRAK = new ResourceLocation(ZoneArtifacts.MODID, "sumrak");
    /** Psi zone: invisible; mobs leave it, players' senses swim ({@link PsiEngine}). */
    public static final ResourceLocation PSI = new ResourceLocation(ZoneArtifacts.MODID, "psi_zone");
    /** Poppy field: its pollen puts to sleep ({@link PoppyEngine}). */
    public static final ResourceLocation POPPY = new ResourceLocation(ZoneArtifacts.MODID, "poppy_field");
    /** Rust: rusty moss, dust, now and then a red-hot patch ({@link RustEngine}). */
    public static final ResourceLocation RUST = new ResourceLocation(ZoneArtifacts.MODID, "rust");

    /** Soap bubbles: drifting gravitational knots that burst at a touch ({@link BubbleEngine}). */
    public static final ResourceLocation BUBBLES = new ResourceLocation(ZoneArtifacts.MODID, "soap_bubbles");
    /** Khlopushka: a glowing clot before one's eyes that flashes and blasts ({@link KhlopushkaEngine}). */
    public static final ResourceLocation KHLOPUSHKA = new ResourceLocation(ZoneArtifacts.MODID, "khlopushka");
    /** Firefly: wandering lights, harmless (client only). */
    public static final ResourceLocation FIREFLY = new ResourceLocation(ZoneArtifacts.MODID, "svetlyachok");
    /** Kamerton: a ball of glass needles that cut whoever doesn't creep through ({@link KamertonEngine}). */
    public static final ResourceLocation KAMERTON = new ResourceLocation(ZoneArtifacts.MODID, "kamerton");
    /** Phantom light: rows of flickering blue lights that fade as one comes near (client only). */
    public static final ResourceLocation FANTOM = new ResourceLocation(ZoneArtifacts.MODID, "fantom_light");
    /** The Web is no zone ({@link WebSavedData}); its id only names it (and routes tuner clicks). */
    public static final ResourceLocation WEB = new ResourceLocation(ZoneArtifacts.MODID, "pautina");

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
