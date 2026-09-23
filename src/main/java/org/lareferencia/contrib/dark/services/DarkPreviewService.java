package org.lareferencia.contrib.dark.services;

import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.worker.CatalogRecordPaginator;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.metadata.IMetadataStore;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.ISnapshotStore;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.CatalogDatabaseManager;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;

/** Synchronous, minter-free payload preview for an explicit selection. */
@Service
public class DarkPreviewService {
    private final ISnapshotStore snapshots;
    private final CatalogDatabaseManager catalog;
    private final IMetadataStore metadataStore;
    private final DarkProperties properties;
    private final DarkOriginalMetadataTransformerService transformer;
    private final DarkLevel1MetadataService level1;
    private final UrlExtractionService urls;
    private final DarkTrackingRepository tracking;

    public DarkPreviewService(ISnapshotStore snapshots, CatalogDatabaseManager catalog, IMetadataStore metadataStore,
            DarkProperties properties, DarkOriginalMetadataTransformerService transformer,
            DarkLevel1MetadataService level1, UrlExtractionService urls, DarkTrackingRepository tracking) {
        this.snapshots = snapshots; this.catalog = catalog; this.metadataStore = metadataStore;
        this.properties = properties; this.transformer = transformer; this.level1 = level1; this.urls = urls;
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
                    String transformed = transformer.transformForMinter(record.getIdentifier(), record.getDatestamp(), original,
                            sourceSchema, properties.getMetadataSchema());
                    String extraction = "xoai".equalsIgnoreCase(sourceSchema) ? original : transformed;
                    OAIRecordMetadata metadata = new OAIRecordMetadata(record.getIdentifier(), extraction);
                    String targetUrl = urls.extractBestUrl(metadata);
                    if (targetUrl == null || targetUrl.isBlank()) throw new IllegalArgumentException("No target URL could be extracted");
                    Map<String, Object> minimal = level1.buildMinimalMetadata(record.getIdentifier(), metadata, targetUrl);
                    DarkTrackingRecord current = naan == null ? null : tracking.findByIdArkNaanAndIdOaiId(naan, record.getIdentifier()).orElse(null);
                    int l1Bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(minimal).length;
                    int l2Bytes = transformed == null ? 0 : transformed.getBytes(StandardCharsets.UTF_8).length;
                    int payloadBytes = l1Bytes + l2Bytes + targetUrl.getBytes(StandardCharsets.UTF_8).length;
                    result.add(new PreviewItem(record.getIdentifier(), current == null ? null : current.getArk(),
                            current == null || current.getState() == null ? null : current.getState().name(), true, null,
                            targetUrl, l1Bytes, l2Bytes, payloadBytes, List.of(), minimal, transformed));
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
