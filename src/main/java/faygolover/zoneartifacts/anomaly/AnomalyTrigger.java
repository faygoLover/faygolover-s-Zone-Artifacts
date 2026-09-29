package faygolover.zoneartifacts.anomaly;

/**
 * How an anomaly's effect fires.
 * <p>
 * Stage 1 fully implements {@link TriggerType#BURST} (fire once, then sit on cooldown).
 * {@link TriggerType#PASSIVE_FIELD} and {@link TriggerType#PHASED} are recognized by the
 * loader so datapacks can already declare them, but {@link faygolover.zoneartifacts.anomaly.AnomalyEngine}
 * does not act on them yet (instances of those types are placed and persisted normally,
 * they just don't do anything until that logic is added in a later stage).
 */
public record AnomalyTrigger(TriggerType type, int cooldownTicks) {

    public enum TriggerType {
        BURST,
        PASSIVE_FIELD,
        PHASED
    }
}
