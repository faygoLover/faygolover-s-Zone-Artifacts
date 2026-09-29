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
    COMET("comet"),
    /** The Comet in soul fire: freezes instead of burning. (New kinds go last: sent by ordinal.) */
    COLD_COMET("cold_comet"),
    /** A clot of chlorine-like gas: bursts into a creeping poisonous cloud. */
    CHEM_COMET("chem_comet"),
    /** Gravi: invisible, passes through everything, leaves a trail of small gravitational pops. */
    GRAVI("gravi");

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
        if (this == COLD_COMET) return ModEntities.COLD_COMET.get();
        if (this == CHEM_COMET) return ModEntities.CHEM_COMET.get();
        if (this == GRAVI) return ModEntities.GRAVI.get();
        return ModEntities.TESLA.get();
    }

    // ---- defaults of a new route (common config) and the speed at x1.0 ------------------

    public double baseSpeed() {
        return switch (this) {
            case COMET -> ModCommonConfig.COMET_BASE_SPEED.get();
            case COLD_COMET -> ModCommonConfig.COLD_COMET_BASE_SPEED.get();
            case CHEM_COMET -> ModCommonConfig.CHEM_COMET_BASE_SPEED.get();
            case GRAVI -> ModCommonConfig.GRAVI_BASE_SPEED.get();
            default -> ModCommonConfig.TESLA_BASE_SPEED.get();
        };
    }

    public int defaultRespawnSeconds() {
        return switch (this) {
            case COMET -> ModCommonConfig.COMET_RESPAWN_SECONDS.get();
            case COLD_COMET -> ModCommonConfig.COLD_COMET_RESPAWN_SECONDS.get();
            case CHEM_COMET -> ModCommonConfig.CHEM_COMET_RESPAWN_SECONDS.get();
            case GRAVI -> 1;
            default -> ModCommonConfig.TESLA_RESPAWN_SECONDS.get();
        };
    }

    public float defaultDamage() {
        return switch (this) {
            case COMET -> ModCommonConfig.COMET_DAMAGE.get().floatValue();
            case COLD_COMET -> ModCommonConfig.COLD_COMET_DAMAGE.get().floatValue();
            case CHEM_COMET -> ModCommonConfig.CHEM_COMET_DAMAGE.get().floatValue();
            case GRAVI -> ModCommonConfig.GRAVI_DAMAGE.get().floatValue();
            default -> ModCommonConfig.TESLA_DAMAGE.get().floatValue();
        };
    }

    public int defaultIntensity() {
        return switch (this) {
            case COMET -> ModCommonConfig.COMET_INTENSITY.get();
            case COLD_COMET -> ModCommonConfig.COLD_COMET_INTENSITY.get();
            case CHEM_COMET -> ModCommonConfig.CHEM_COMET_INTENSITY.get();
            case GRAVI -> ModCommonConfig.GRAVI_INTENSITY.get();
            default -> ModCommonConfig.TESLA_INTENSITY.get();
        };
    }

    public double defaultChaseRadius() {
        return switch (this) {
            case COMET -> ModCommonConfig.COMET_CHASE_RADIUS.get();
            case COLD_COMET -> ModCommonConfig.COLD_COMET_CHASE_RADIUS.get();
            case CHEM_COMET -> ModCommonConfig.CHEM_COMET_CHASE_RADIUS.get();
            case GRAVI -> ModCommonConfig.GRAVI_CHASE_RADIUS.get();
            default -> ModCommonConfig.TESLA_CHASE_RADIUS.get();
        };
    }

    /** Russian name for command output. */
    public String displayName() {
        return switch (this) {
            case COMET -> "Комета";
            case COLD_COMET -> "Холодная комета";
            case CHEM_COMET -> "Химическая комета";
            case GRAVI -> "Грави";
            default -> "Тесла";
        };
    }
}
