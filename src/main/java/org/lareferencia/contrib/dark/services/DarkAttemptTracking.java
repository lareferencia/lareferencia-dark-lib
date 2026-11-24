package org.lareferencia.contrib.dark.services;

import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecordId;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Commits the last attempted operation on the existing tracking row before transport starts. */
@Service
public class DarkAttemptTracking {
    public record Item(String oaiId, String ark, String payloadHash, String sourceHash, String target,
                       Long networkId, Long snapshotId) {}
    public record Batch(String naan, List<Item> items) {
        public Batch {
            items = List.copyOf(items);
            if (items.isEmpty() || naan == null || naan.isBlank())
                throw new IllegalArgumentException("A tracked request requires a NAAN and at least one record");
        }
    }

    private final DarkTrackingRepository tracking;
    private final DarkErrorCodec errors;

    public DarkAttemptTracking(DarkTrackingRepository tracking, DarkErrorCodec errors) {
        this.tracking = tracking;
        this.errors = errors;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPending(String operation, Batch batch) {
        for (Item item : batch.items()) {
            var id = DarkTrackingRecordId.of(batch.naan(), item.oaiId());
            var row = tracking.findById(id).orElseGet(DarkTrackingRecord::new);
            if (!java.util.Objects.equals(row.getArk(), item.ark())) {
                throw new IllegalStateException("Tracked ARK changed before sending " + item.oaiId());
            }
            row.setId(id);
            row.setState(DarkTrackingState.ERROR);
            row.setSourceNetworkId(item.networkId());
            row.setSourceSnapshotId(item.snapshotId());
            row.setSourceMetadataHash(item.sourceHash());
            row.setTargetUrl(item.target());
            row.setLastError(errors.encode("REMOTE_TRANSIENT", DarkMinterClientException.MODIFICATION_OUTCOME_UNKNOWN,
                    operation, null, false, "Minter response has not been confirmed",
                    "STAGE".equals(operation) ? Map.of("attemptedPayloadHash", item.payloadHash()) : Map.of()));
            tracking.save(row);
        }
    }
}
