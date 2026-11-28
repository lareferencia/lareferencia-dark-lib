package org.lareferencia.contrib.dark.services;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.lareferencia.contrib.dark.services.DarkPreparationException.Reason;
import org.lareferencia.contrib.dark.worker.CatalogRecordPaginator;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.metadata.IMetadataStore;
import org.lareferencia.core.metadata.ISnapshotStore;
import org.lareferencia.core.metadata.MDFormatTranformationException;
import org.lareferencia.core.metadata.MetadataRecordStoreException;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.CatalogDatabaseManager;
import org.lareferencia.core.worker.validation.ValidationException;
import org.springframework.stereotype.Service;

/** Evaluates local deposit preparation for a fixed harvest and profile. */
@Service
public class DarkCollectionEvaluator {
    private static final List<String> FIELDS = List.of("authors", "year", "resource_type", "rights");
    private static final int EXAMPLES_PER_REASON = 5;

    public record Source(SnapshotMetadata snapshot, String sourceSchema, CatalogRecordPaginator pages) {}
    public record Example(String oaiId, String detail) {}
    public record Failure(Reason reason, long count, List<Example> examples) {}
    public record Report(long processed, long ready, Map<String, Long> coverage, List<Failure> failures) {}

    private final ISnapshotStore snapshots;
    private final CatalogDatabaseManager catalog;
    private final IMetadataStore metadata;
    private final DarkStagePayloadService payloads;

    public DarkCollectionEvaluator(ISnapshotStore snapshots, CatalogDatabaseManager catalog,
            IMetadataStore metadata, DarkStagePayloadService payloads) {
        this.snapshots = snapshots;
        this.catalog = catalog;
        this.metadata = metadata;
        this.payloads = payloads;
    }

    public Source latest(Network network) {
        Long id = snapshots.findLastHarvestingSnapshot(network.getId());
        if (id == null) return null;
        var snapshot = snapshots.getSnapshotMetadata(id);
        var pages = CatalogRecordPaginator.readOnly(snapshot, catalog);
        return new Source(snapshot, network.getMetadataStoreSchema(), pages);
    }

    public void evaluate(Source source, Long transformerId, String targetSchema, Consumer<Report> progress) {
        var profile = payloads.loadProfile(transformerId);
        var format = payloads.depositFormat(profile, source.sourceSchema(), targetSchema);
        var tally = new Tally();
        var pages = source.pages();
        int pageCount = pages.getTotalPages();
        for (int page = 0; page < pageCount; page++) {
            for (var record : pages.nextPage()) {
                try {
                    String original = metadata.getMetadata(source.snapshot(), record.getOriginalMetadataHash());
                    if (original == null || original.isBlank()) {
                        throw new DarkPreparationException(Reason.SOURCE_METADATA, "Original metadata is missing");
                    }
                    var request = payloads.prepare(profile, source.snapshot(), record, original, format);
                    tally.accept(request.getMinimalMetadata());
                } catch (Exception e) {
                    tally.reject(record.getIdentifier(), e);
                }
            }
            progress.accept(tally.report());
        }
        if (tally.processed != pages.getTotalCount()) {
            throw new IllegalStateException("The active record count changed during evaluation");
        }
        if (pageCount == 0) progress.accept(tally.report());
    }

    private static Reason reason(Exception error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DarkPreparationException e) return e.getReason();
            if (cause instanceof OAIRecordMetadataParseException) return Reason.INVALID_XML;
            if (cause instanceof MetadataRecordStoreException) return Reason.SOURCE_METADATA;
            if (cause instanceof ValidationException || cause instanceof MDFormatTranformationException) return Reason.TRANSFORMATION;
        }
        return Reason.PREPARATION;
    }

    // The worker owns the counters. Polling clients receive immutable snapshots after each page.
    private static class Tally {
        long processed;
        long ready;
        final Map<String, Long> coverage = new LinkedHashMap<>();
        final Map<Reason, Long> counts = new EnumMap<>(Reason.class);
        final Map<Reason, List<Example>> examples = new EnumMap<>(Reason.class);

        Tally() {
            FIELDS.forEach(field -> coverage.put(field, 0L));
        }

        void accept(Map<String, Object> fields) {
            processed++;
            ready++;
            for (String field : FIELDS) {
                if (fields.get(field) != null) coverage.merge(field, 1L, Long::sum);
            }
        }

        void reject(String id, Exception e) {
            processed++;
            Reason reason = reason(e);
            counts.merge(reason, 1L, Long::sum);
            var sample = examples.computeIfAbsent(reason, key -> new ArrayList<>());
            if (sample.size() < EXAMPLES_PER_REASON) {
                String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                sample.add(new Example(id, detail.substring(0, Math.min(240, detail.length()))));
            }
        }

        Report report() {
            return new Report(processed, ready, Map.copyOf(coverage), counts.entrySet().stream()
                    .map(entry -> new Failure(entry.getKey(), entry.getValue(), List.copyOf(examples.get(entry.getKey()))))
                    .toList());
        }
    }
}
