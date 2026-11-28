package org.lareferencia.contrib.dark.services;

/** A preparation failure with a stable reason shared by staging and collection evaluation. */
public class DarkPreparationException extends IllegalArgumentException {
    public enum Reason { MISSING_TITLE, MISSING_TARGET, SOURCE_METADATA, INVALID_XML, TRANSFORMATION, PREPARATION }

    private final Reason reason;

    public DarkPreparationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
