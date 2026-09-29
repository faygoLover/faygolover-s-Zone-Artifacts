package faygolover.zoneartifacts.anomaly;

/**
 * Which categories of entity can trip the anomaly's zone.
 * "thrownProjectiles" covers snowballs, eggs, arrows, etc. — per the design, these trip the
 * anomaly (counts as a hit for cooldown/trigger-effect purposes) but never take damage from it.
 */
public record AnomalyDetect(boolean players, boolean mobs, boolean thrownProjectiles) {
}
