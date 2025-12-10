package org.lareferencia.contrib.dark.domain;

import java.util.Optional;

/**
 * Simple record to hold context for DARK processing of an OAI record.
 *
 * @param oaiId    The OAI identifier
 * @param url      The extracted best URL for the record
 * @param existing The existing DARK tracking entry, if any
 */
public record DarkRecordContext(
        String oaiId,
        String url,
        Optional<DarkIdentifier> existing
) {
    
    /**
     * Returns the DARK ID from tracking, or null if not tracked.
     */
    public String getDarkId() {
        return existing.map(DarkIdentifier::getDarkId).orElse(null);
    }

    /**
     * Checks if this record is already tracked in the DARK system.
     */
    public boolean isTracked() {
        return existing.isPresent();
    }

    /**
     * Checks if the URL has changed from the tracked URL.
     */
    public boolean hasUrlChanged() {
        return existing.map(e -> !url.equals(e.getUrl())).orElse(false);
    }
}
