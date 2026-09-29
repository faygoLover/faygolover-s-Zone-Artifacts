package faygolover.zoneartifacts.tuner;

/**
 * What a tuner changes, and by how much per click (left-click lowers, right-click raises; the
 * larger step is used while sneaking). The ranges are enforced in {@link TunerService}.
 */
public enum TunerKind {
    /** Size in blocks, 1 .. maxSize. Electra: the zone; Tesla: the ball and its hitbox. */
    SIZE("size", 0.1, 1.0),
    /** Speed multiplier, x0.0 .. maxSpeedMultiplier. Only anomalies that move (the Tesla). */
    SPEED("speed", 0.1, 1.0),
    /** Seconds, 1 .. maxCooldownSeconds. Electra: cooldown; Tesla: respawn delay. */
    COOLDOWN("cooldown", 1.0, 5.0),
    /** Visual intensity (number of arcs/loops), 1 .. maxIntensity. */
    INTENSITY("intensity", 1.0, 5.0),
    /** Damage per hit in half-hearts, 0 .. maxDamage. */
    DAMAGE("damage", 0.5, 2.0);

    private final String key;
    private final double step;
    private final double sneakStep;

    TunerKind(String key, double step, double sneakStep) {
        this.key = key;
        this.step = step;
        this.sneakStep = sneakStep;
    }

    public String key() {
        return key;
    }

    public double step(boolean sneaking) {
        return sneaking ? sneakStep : step;
    }

    public String translationKey() {
        return "tuner.fl_zone_arts." + key;
    }

    public static TunerKind byOrdinal(int ordinal) {
        TunerKind[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SIZE;
    }
}
