package faygolover.zoneartifacts.entity;

/**
 * The always-on "ball of lightning" Tesla flies around wrapped in — a small, closed loop of arcs
 * hugging the entity itself, much more concentrated than Electra's zone-spanning bundles (see
 * {@code client.TeslaVisualRenderer}). {@code radius} is the anchor-sampling radius around the
 * entity's own center, in blocks.
 */
public record TeslaArcVisual(int bundleCount, int pointsPerBundle, int minLifetimeTicks, int maxLifetimeTicks,
                              int color, double radius) {
}
