package org.lareferencia.contrib.dark.client;

import java.time.Instant;

/** The minter explicitly rejected this reservation before allocating identifiers. */
public class ReservationDeferred extends DarkMinterClientException {
    public static final String AUTHORITY_STATUS_UNAVAILABLE = "AUTHORITY_STATUS_UNAVAILABLE";
    private final Instant retryAt;

    ReservationDeferred(Instant retryAt) {
        super(503, AUTHORITY_STATUS_UNAVAILABLE, true, "Reservation deferred until " + retryAt);
        this.retryAt = retryAt;
    }

    public Instant getRetryAt() {
        return retryAt;
    }
}
