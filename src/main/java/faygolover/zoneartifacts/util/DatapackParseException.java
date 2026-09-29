package faygolover.zoneartifacts.util;

/**
 * Thrown while parsing any datapack-driven config record (an anomaly type, a Tesla type, ...) to
 * name exactly which field was wrong. Callers catch this at the top of one file's parse and log
 * {@code field} + the message, then skip just that file — see {@code AnomalyTypeManager} and
 * {@code TeslaTypeManager} for the calling convention.
 */
public final class DatapackParseException extends RuntimeException {

    public final String field;

    public DatapackParseException(String field, String message) {
        super(message);
        this.field = field;
    }
}
