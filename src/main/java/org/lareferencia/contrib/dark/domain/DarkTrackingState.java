package org.lareferencia.contrib.dark.domain;

public enum DarkTrackingState {
    RESERVED("R"),
    DRAFT("D"),
    UPDATE("U"),
    PUBLISHED("P"),
    TOMBSTONE("T"),
    ERROR("E");

    private final String value;

    DarkTrackingState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
