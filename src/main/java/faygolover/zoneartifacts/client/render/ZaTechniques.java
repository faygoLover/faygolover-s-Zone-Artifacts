package faygolover.zoneartifacts.client.render;

import faygolover.flrender.api.FlRender;
import faygolover.flrender.api.Quality;
import faygolover.flrender.api.Technique;
import faygolover.zoneartifacts.ZoneArtifacts;

/**
 * The anomalies' graphics techniques in FL Render's settings tab (names in our lang:
 * {@code flrender.technique.fl_zone_arts.<key>}). At LOW quality the anomalies are drawn the plain way.
 */
public final class ZaTechniques {

    /** Refraction round the anomalies: the plain mesh refraction is cheap, so on at every quality. */
    public static final Technique AIR_DISTORTION = FlRender.technique(ZoneArtifacts.MODID, "air_distortion", Quality.LOW);
    public static final Technique PIXEL_DISTORTION = FlRender.technique(ZoneArtifacts.MODID, "pixel_distortion", Quality.MEDIUM);
    public static final Technique SOFT_EDGES = FlRender.technique(ZoneArtifacts.MODID, "soft_edges", Quality.MEDIUM);
    public static final Technique DITHERING = FlRender.technique(ZoneArtifacts.MODID, "dithering", Quality.MEDIUM);
    public static final Technique BUBBLE_FILM = FlRender.technique(ZoneArtifacts.MODID, "bubble_film", Quality.MEDIUM);
    public static final Technique GLOW_HALO = FlRender.technique(ZoneArtifacts.MODID, "glow_halo", Quality.HIGH);
    public static final Technique VOLUMETRIC_SMOKE = FlRender.technique(ZoneArtifacts.MODID, "volumetric_smoke", Quality.HIGH);

    private ZaTechniques() {
    }

    /** Registers them (loading the class does). */
    static void init() {
    }
}
