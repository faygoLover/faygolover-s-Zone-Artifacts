package faygolover.zoneartifacts.anomaly;

/**
 * Configures Electra-style ambient lightning: several "bundles", each a jagged arc chaining
 * through {@code pointsPerBundle} random points inside the zone, staying put for somewhere
 * between {@code minLifetimeTicks} and {@code maxLifetimeTicks} before picking new points —
 * matching the source material's "semi-stable arcs, several at once, not refreshing in sync"
 * look rather than particles constantly popping in and out.
 * <p>
 * Entirely a client-side rendering concern (see {@code AnomalyArcRenderer}) — the server only
 * parses and syncs this config, it never simulates the arcs itself, so it costs nothing
 * server-side beyond what ambient sound already costs to sync.
 */
public record AnomalyArcEffect(
        int bundleCount,
        int pointsPerBundle,
        int minLifetimeTicks,
        int maxLifetimeTicks,
        int color
) {
}
