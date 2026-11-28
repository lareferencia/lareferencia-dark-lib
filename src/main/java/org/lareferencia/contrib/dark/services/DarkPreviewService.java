package org.lareferencia.contrib.dark.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lareferencia.contrib.dark.client.StageArkRequest;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.worker.CatalogRecordPaginator;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.metadata.IMetadataStore;
import org.lareferencia.core.metadata.ISnapshotStore;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.CatalogDatabaseManager;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Synchronous, minter-free payload preview for an explicit selection. */
@Service
public class DarkPreviewService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ISnapshotStore snapshots;
    private final CatalogDatabaseManager catalog;
    private final IMetadataStore metadataStore;
    private final DarkProperties properties;
    private final DarkStagePayloadService payloads;
    private final DarkTrackingRepository tracking;

    public DarkPreviewService(ISnapshotStore snapshots, CatalogDatabaseManager catalog, IMetadataStore metadataStore,
            DarkProperties properties, DarkStagePayloadService payloads, DarkTrackingRepository tracking) {
        this.snapshots = snapshots;
        this.catalog = catalog;
        this.metadataStore = metadataStore;
        this.properties = properties;
        this.payloads = payloads;
        this.tracking = tracking;
    }

    public List<PreviewItem> preview(Network network, List<String> oaiIds) {
        Long snapshotId = snapshots.findLastHarvestingSnapshot(network);
        if (snapshotId == null) return oaiIds.stream().map(id -> PreviewItem.error(id, null, null, "NO_SNAPSHOT", "No harvested snapshot is available")).toList();
        return preview(network, snapshotId, oaiIds);
    }

    /** Previews against the exact source snapshot stored with the tracking payload. */
    public List<PreviewItem> preview(Network network, Long snapshotId, List<String> oaiIds) {
        if (snapshotId == null) {
            return oaiIds.stream().map(id -> PreviewItem.error(id, null, null,
                    "SOURCE_SNAPSHOT_MISSING", "No source snapshot is recorded for this payload")).toList();
        }
        SnapshotMetadata snapshot = snapshots.getSnapshotMetadata(snapshotId);
        String sourceSchema = network.getMetadataStoreSchema();
        String targetSchema = properties.getMetadataSchema();
        var profile = payloads.loadProfile(properties.getL1TransformerId());
        List<PreviewItem> result = new ArrayList<>();
        CatalogRecordPaginator paginator = new CatalogRecordPaginator(snapshot, catalog, oaiIds);
        paginator.setPageSize(Math.max(100, oaiIds.size()));
        LinkedHashSet<String> found = new LinkedHashSet<>();
        String naan = network.getAttributes() == null ? null : String.valueOf(network.getAttributes().get("ark_naan"));
        try {
            for (OAIRecord record : paginator.nextPage().getContent()) {
                found.add(record.getIdentifier());
                try {
                    String original = metadataStore.getMetadata(snapshot, record.getOriginalMetadataHash());
                    StageArkRequest request = payloads.prepare(profile, snapshot, record, original, sourceSchema, targetSchema);
                    String transformed = request.getOriginalMetadata();
                    Map<String, Object> minimal = request.getMinimalMetadata();
                    DarkTrackingRecord current = naan == null ? null : tracking.findByIdArkNaanAndIdOaiId(naan, record.getIdentifier()).orElse(null);
                    int l1Bytes = JSON.writeValueAsBytes(minimal).length;
                    int l2Bytes = transformed == null ? 0 : transformed.getBytes(StandardCharsets.UTF_8).length;
                    int payloadBytes = JSON.writeValueAsBytes(request).length;
                    result.add(new PreviewItem(record.getIdentifier(), current == null ? null : current.getArk(),
                            current == null || current.getState() == null ? null : current.getState().name(), true, null,
                            request.getTarget(), l1Bytes, l2Bytes, payloadBytes, List.of(), minimal, transformed));
                } catch (Exception e) {
                    result.add(PreviewItem.error(record.getIdentifier(), null, null, "VALIDATION", bounded(e.getMessage())));
                }
            }
            for (String requested : oaiIds) {
                if (!found.contains(requested)) result.add(PreviewItem.error(requested, null, null,
                        "RECORD_NOT_IN_NETWORK", "The OAI identifier is not present in the selected network snapshot"));
            }
        } finally {
            catalog.closeDataSource(snapshot.getSnapshotId());
        }
        return result;
    }

    /** Returns the selected identifiers that are not present in this network's latest snapshot. */
    public List<String> missingFromNetwork(Network network, List<String> oaiIds) {
        Long snapshotId = snapshots.findLastHarvestingSnapshot(network);
        if (snapshotId == null) return List.copyOf(oaiIds);
        SnapshotMetadata snapshot = snapshots.getSnapshotMetadata(snapshotId);
        CatalogRecordPaginator paginator = new CatalogRecordPaginator(snapshot, catalog, oaiIds);
        paginator.setPageSize(Math.max(100, oaiIds.size()));
        java.util.Set<String> found = new java.util.HashSet<>();
        try {
            for (OAIRecord record : paginator.nextPage().getContent()) found.add(record.getIdentifier());
        } finally {
            catalog.closeDataSource(snapshot.getSnapshotId());
        }
        return oaiIds.stream().filter(id -> !found.contains(id)).toList();
    }

    private String bounded(String message) { return message == null ? "Validation failed" : message.substring(0, Math.min(1200, message.length())); }

    public record PreviewItem(String oaiId, String ark, String localState, boolean valid, String error,
            String targetUrl, Integer l1Bytes, Integer l2Bytes, Integer payloadBytes, List<String> warnings,
            Map<String, Object> minimalMetadata, String originalMetadata) {
        static PreviewItem error(String id, String ark, String localState, String code, String message) {
            return new PreviewItem(id, ark, localState, false, code + ": " + message, null, null, null, null, List.of(), Map.of(), null);
        }
    }
}
