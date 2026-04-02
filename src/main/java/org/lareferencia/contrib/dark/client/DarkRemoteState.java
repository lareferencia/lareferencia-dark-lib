package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;

public enum DarkRemoteState {
    RESERVED("R"),
    DRAFT("D"),
    UPDATE("U"),
    PUBLISHED("P"),
    TOMBSTONE("T");

    private final String value;

    DarkRemoteState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DarkRemoteState fromValue(String value) {
        for (DarkRemoteState candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown dARK state: " + value);
    }

    public DarkTrackingState toTrackingState() {
        return switch (this) {
            case RESERVED -> DarkTrackingState.RESERVED;
            case DRAFT -> DarkTrackingState.DRAFT;
            case UPDATE -> DarkTrackingState.UPDATE;
            case PUBLISHED -> DarkTrackingState.PUBLISHED;
            case TOMBSTONE -> DarkTrackingState.TOMBSTONE;
        };
    }
}
