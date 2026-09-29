package faygolover.zoneartifacts.tesla;

import faygolover.zoneartifacts.config.ModCommonConfig;
import faygolover.zoneartifacts.registry.ModEntities;
import net.minecraft.world.entity.EntityType;

/**
 * What flies a route: the Tesla (ball of lightning) or the Comet (fireball). Both share the whole
 * route system — placer clicks, drafts, saved routes, tuners, chasing flagged players — and only
 * differ in what happens on impact and how they look. Stored with the route; routes saved before
 * 0.1.8.0 are Tesla routes.
 */
public enum RouteKind {
    TESLA("tesla"),
    COMET("comet");

    private final String id;

    RouteKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Translation key of the anomaly's name ({@code anomaly.fl_zone_arts.tesla}, {@code ...comet}). */
    public String nameKey() {
        return "anomaly.fl_zone_arts." + id;
    }

    public static RouteKind byId(String id) {
        for (RouteKind kind : values()) {
            if (kind.id.equals(id)) return kind;
        }
        return TESLA;
    }

    public static RouteKind byOrdinal(int ordinal) {
        RouteKind[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : TESLA;
    }

    public EntityType<? extends TeslaEntity> entityType() {
        if (this == COMET) return ModEntities.COMET.get();
        return ModEntities.TESLA.get();
    }

    // ---- defaults of a new route (common config) and the speed at x1.0 ------------------

    public double baseSpeed() {
        return this == COMET ? ModCommonConfig.COMET_BASE_SPEED.get() : ModCommonConfig.TESLA_BASE_SPEED.get();
    }

    public int defaultRespawnSeconds() {
        return this == COMET ? ModCommonConfig.COMET_RESPAWN_SECONDS.get() : ModCommonConfig.TESLA_RESPAWN_SECONDS.get();
    }

    public float defaultDamage() {
        return (this == COMET ? ModCommonConfig.COMET_DAMAGE.get() : ModCommonConfig.TESLA_DAMAGE.get()).floatValue();
    }

    public int defaultIntensity() {
        return this == COMET ? ModCommonConfig.COMET_INTENSITY.get() : ModCommonConfig.TESLA_INTENSITY.get();
    }

    public double defaultChaseRadius() {
        return this == COMET ? ModCommonConfig.COMET_CHASE_RADIUS.get() : ModCommonConfig.TESLA_CHASE_RADIUS.get();
    }
}
