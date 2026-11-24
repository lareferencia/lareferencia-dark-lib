package org.lareferencia.contrib.dark.services;

import org.lareferencia.contrib.dark.client.ARKResponse;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.lareferencia.contrib.dark.client.DarkRemoteState;
import org.lareferencia.contrib.dark.client.ReservationDeferred;
import org.lareferencia.contrib.dark.client.ReserveBatchResponse;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecordId;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.function.Supplier;

/** Receipts commit independently of the worker page. Projection and its acknowledgement commit together. */
@Service
public class DarkAttemptJournal {
    public record Item(String oaiId, String ark, String payloadHash, String sourceHash, String target,
                       Long networkId, Long snapshotId) {}
    public record Batch(String naan, List<Item> items) {
        public Batch {
            items = List.copyOf(items);
            if (items.isEmpty() || naan == null || naan.isBlank())
                throw new IllegalArgumentException("A tracked request requires a NAAN and at least one record");
        }
    }
    private final JdbcTemplate db;
    private final DarkTrackingRepository tracking;
    private final TransactionTemplate page;
    private final TransactionTemplate independent;
    private final DarkErrorCodec errors;

    public DarkAttemptJournal(JdbcTemplate db, DarkTrackingRepository tracking, PlatformTransactionManager tx, DarkErrorCodec errors) {
        this.db = db;
        this.tracking = tracking;
        this.errors = errors;
        page = new TransactionTemplate(tx);
        independent = new TransactionTemplate(tx);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private String canonical(String url) { return url.replaceFirst("/+$", ""); }

    public void checkDestination(String url, String authority) {
        if (db.queryForObject("select count(*) from dark_tracking_installation where installation_id is not null and (base_url <> ? or authority_id <> ?)", Integer.class,
                canonical(url), Objects.toString(authority, "")) != 0)
            throw new IllegalArgumentException("The tracking catalogue belongs to a different minter or authority");
    }

    /** Called before counting work, and inside each page transaction before loading tracking entities. */
    public void prepare(String url, String authority) {
        if (authority == null || authority.isBlank()) throw new IllegalStateException("Configure the minter authority first");
        page.executeWithoutResult(tx -> {
            UUID installation = db.queryForObject("select installation_id from dark_tracking_installation where id=1 for update", UUID.class);
            if (installation == null) {
                if (tracking.count() != 0)
                    throw new IllegalStateException("Existing dARK tracking must be explicitly associated with its original minter and authority");
                installation = UUID.randomUUID();
                db.update("update dark_tracking_installation set installation_id=?, base_url=?, authority_id=? where id=1",
                        installation, canonical(url), authority);
            }
            checkDestination(url, authority);
            recover(installation, authority);
        });
    }

    public <T> T send(String url, String authority, String operation, Batch batch, Supplier<T> send) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("A dARK modification requires a tracking transaction");
        UUID request = UUID.randomUUID();
        independent.executeWithoutResult(tx -> {
            UUID installation = db.queryForObject("select installation_id from dark_tracking_installation where id=1 and base_url=? and authority_id=?",
                    UUID.class, canonical(url), authority);
            for (Item item : batch.items()) {
                db.update("""
                        insert into dark_minter_attempt (request_id,installation_id,base_url,authority_id,operation,
                        ark_naan,oai_id,ark,payload_hash,source_hash,target_url,network_id,snapshot_id)
                        values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, request, installation, url, authority, operation, batch.naan(), item.oaiId(), item.ark(),
                        item.payloadHash(), item.sourceHash(), item.target(), item.networkId(), item.snapshotId());
            }
        });
        T response;
        try {
            response = send.get();
        } catch (DarkMinterClientException failure) {
            boolean refused = (!failure.isRetryable() && failure.getStatusCode() >= 400 && failure.getStatusCode() < 500
                    && !"SIGNED_REQUEST_REPLAY".equals(failure.getErrorCode()))
                    || ReservationDeferred.AUTHORITY_STATUS_UNAVAILABLE.equals(failure.getErrorCode()) && failure.getStatusCode() == 503;
            independent.executeWithoutResult(tx -> db.update("update dark_minter_attempt set finished_at=current_timestamp,outcome=?,error_code=? where request_id=?",
                    refused ? "REFUSED" : "UNKNOWN", failure.getErrorCode(), request));
            if (refused) acknowledge(request);
            throw failure;
        }
        independent.executeWithoutResult(tx -> {
            if (response instanceof ARKResponse result) {
                Item item = batch.items().get(0);
                if (Objects.equals(item.ark(), result.getArk()) && result.getState() != null)
                    accepted(request, item.oaiId(), result.getArk(), result.getState());
            } else if (response instanceof ReserveBatchResponse result) {
                for (var row : Objects.requireNonNullElse(result.getResults(), List.<ARKResponse>of())) {
                    if (row != null && row.getArk() != null && !row.getArk().isBlank())
                        accepted(request, row.getClientItemId(), row.getArk(), DarkRemoteState.RESERVED);
                }
                for (var error : Objects.requireNonNullElse(result.getErrors(), List.<ReserveBatchResponse.BatchError>of())) {
                    if (error != null) db.update("update dark_minter_attempt set outcome='REFUSED',finished_at=current_timestamp where request_id=? and oai_id=? and outcome='UNKNOWN'",
                            request, error.getClientItemId());
                }
            }
        });
        acknowledge(request);
        return response;
    }

    private void accepted(UUID request, String id, String ark, DarkRemoteState state) {
        db.update("update dark_minter_attempt set outcome='ACCEPTED', ark=?,remote_state=?,finished_at=current_timestamp where request_id=? and oai_id=?",
                ark, state.name(), request, id);
    }

    private void acknowledge(UUID request) {
        db.update("update dark_minter_attempt set applied=true where request_id=? and outcome <> 'UNKNOWN'", request);
    }

    private void recover(UUID installation, String authority) {
        for (var row : db.queryForList("select * from dark_minter_attempt where applied=false order by sequence_id")) {
            if (!installation.equals(row.get("installation_id")) || !authority.equals(row.get("authority_id")))
                throw new IllegalStateException("Pending dARK attempts belong to another installation or authority");
            String operation = (String) row.get("operation");
            String outcome = (String) row.get("outcome");
            var id = DarkTrackingRecordId.of((String) row.get("ark_naan"), (String) row.get("oai_id"));
            if (!"REFUSED".equals(outcome)) {
                var record = tracking.findById(id).orElseGet(DarkTrackingRecord::new);
                record.setId(id);
                record.setSourceNetworkId((Long) row.get("network_id"));
                record.setSourceSnapshotId((Long) row.get("snapshot_id"));
                record.setSourceMetadataHash((String) row.get("source_hash"));
                record.setTargetUrl((String) row.get("target_url"));
                if (row.get("ark") != null) record.setArk((String) row.get("ark"));
                if ("ACCEPTED".equals(outcome)) {
                    var state = DarkRemoteState.valueOf((String) row.get("remote_state"));
                    record.setState(state.toTrackingState());
                    record.setLastError(null);
                    if ("STAGE".equals(operation)) {
                        record.setStagePayloadHash((String) row.get("payload_hash"));
                        LocalDateTime finished = ((java.sql.Timestamp) row.get("finished_at")).toLocalDateTime();
                        record.setLastStagedAt(finished);
                        if (state == DarkRemoteState.PUBLISHED) record.setPublishedAt(finished);
                    }
                } else {
                    record.setState(DarkTrackingState.ERROR);
                    record.setLastError(errors.encode("REMOTE_TRANSIENT", DarkMinterClientException.MODIFICATION_OUTCOME_UNKNOWN,
                            operation, null, false, "Recovery found an attempt without a confirmed response", Map.of()));
                }
                tracking.save(record);
            }
            db.update("update dark_minter_attempt set applied=true where sequence_id=?", row.get("sequence_id"));
        }
    }
}
