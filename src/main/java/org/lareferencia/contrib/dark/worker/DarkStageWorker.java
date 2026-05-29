package org.lareferencia.contrib.dark.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.contrib.dark.client.ARKResponse;
import org.lareferencia.contrib.dark.client.DarkMinterClient;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.lareferencia.contrib.dark.client.DarkRemoteState;
import org.lareferencia.contrib.dark.client.ReserveBatchResponse;
import org.lareferencia.contrib.dark.client.StageArkRequest;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecordId;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.DarkLevel1MetadataService;
import org.lareferencia.contrib.dark.services.DarkNetworkSettingsResolver;
import org.lareferencia.contrib.dark.services.DarkOriginalMetadataTransformerService;
import org.lareferencia.contrib.dark.services.DarkProperties;
import org.lareferencia.contrib.dark.services.UrlExtractionService;
import org.lareferencia.core.metadata.IMetadataStore;
import org.lareferencia.core.metadata.ISnapshotStore;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.CatalogDatabaseManager;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.lareferencia.core.service.management.SnapshotLogService;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class DarkStageWorker extends BaseBatchWorker<OAIRecord, NetworkRunningContext> {

    private static final Logger logger = LogManager.getLogger(DarkStageWorker.class);

    @Autowired
    private ISnapshotStore snapshotStore;

    @Autowired
    private CatalogDatabaseManager catalogDatabaseManager;

    @Autowired
    private IMetadataStore metadataStore;

    @Autowired
    private DarkProperties darkProperties;

    @Autowired
    private UrlExtractionService urlExtractionService;

    @Autowired
    private DarkLevel1MetadataService level1MetadataService;

    @Autowired
    private DarkTrackingRepository darkTrackingRepository;

    @Autowired
    private DarkMinterClient darkMinterClient;

    @Autowired
    private DarkNetworkSettingsResolver darkNetworkSettingsResolver;

    @Autowired
    private SnapshotLogService snapshotLogService;

    @Autowired
    private DarkOriginalMetadataTransformerService darkOriginalMetadataTransformerService;

    private SnapshotMetadata snapshotMetadata;
    private String currentArkNaan;
    private String currentSourceMetadataSchema;
    private String currentTargetMetadataSchema;
    private final List<DarkStageCandidate> recordsToReserve = new ArrayList<>();
    private final List<DarkStageCandidate> recordsToStage = new ArrayList<>();
    private int pageProcessed;
    private int pageQueuedForReserve;
    private int pageQueuedForStage;
    private int pageSkippedUnchanged;
    private int pageErrors;
    private int pageStageSuccesses;
    private int pageReserveFailures;
    private int pageStageFailures;
    private int runProcessed;
    private int runQueuedForReserve;
    private int runQueuedForStage;
    private int runSkippedUnchanged;
    private int runErrors;
    private int runStageSuccesses;
    private int runReserveFailures;
    private int runStageFailures;
    private boolean pageHaltedBySystemicError;

    @Override
    protected void preRun() {
        validateConfiguration();
        setPageSize(darkProperties.getStagePageSize());
        resetRunCounters();
        currentArkNaan = null;
        currentSourceMetadataSchema = runningContext.getNetwork().getMetadataStoreSchema();
        currentTargetMetadataSchema = darkProperties.getMetadataSchema();

        if (currentSourceMetadataSchema == null || currentSourceMetadataSchema.isBlank()) {
            logError("DARK stage cannot start for network " + runningContext.getNetwork().getAcronym()
                    + ": network.metadataStoreSchema must be configured");
            setPaginator(null);
            stop();
            return;
        }

        try {
            currentArkNaan = darkNetworkSettingsResolver.resolveArkNaan(runningContext.getNetwork());
        } catch (IllegalStateException e) {
            logError("DARK stage cannot start for network " + runningContext.getNetwork().getAcronym() + ": " + e.getMessage());
            setPaginator(null);
            stop();
            return;
        }

        Long snapshotId = snapshotStore.findLastHarvestingSnapshot(runningContext.getNetwork());
        if (snapshotId == null) {
            logWarn("No harvested snapshot found for " + runningContext.getNetwork().getAcronym());
            setPaginator(null);
            stop();
            return;
        }

        snapshotMetadata = snapshotStore.getSnapshotMetadata(snapshotId);
        logInfo(String.format(
                "DARK stage run configured for network %s using snapshot %s | authorityId=%s | arkNaan=%s | minterBaseUrl=%s | stagePageSize=%s | reserveBatchSize=%s",
                runningContext.getNetwork().getAcronym(),
                snapshotId,
                darkProperties.getAuthorityId(),
                currentArkNaan,
                darkProperties.getMinter().getBaseUrl(),
                darkProperties.getStagePageSize(),
                darkProperties.getReserveBatchSize())
                + String.format(" | sourceMetadataSchema=%s | targetMetadataSchema=%s | schemaTransform=%s",
                currentSourceMetadataSchema,
                currentTargetMetadataSchema,
                !currentSourceMetadataSchema.equals(currentTargetMetadataSchema)));
        CatalogRecordPaginator paginator = new CatalogRecordPaginator(snapshotMetadata, catalogDatabaseManager);
        paginator.setPageSize(getPageSize());
        setPaginator(paginator);
    }

    @Override
    public void prePage() {
        recordsToReserve.clear();
        recordsToStage.clear();
        pageProcessed = 0;
        pageQueuedForReserve = 0;
        pageQueuedForStage = 0;
        pageSkippedUnchanged = 0;
        pageErrors = 0;
        pageStageSuccesses = 0;
        pageReserveFailures = 0;
        pageStageFailures = 0;
        pageHaltedBySystemicError = false;
    }

    @Override
    public void processItem(OAIRecord record) {
        pageProcessed++;
        runProcessed++;

        Optional<DarkTrackingRecord> existing = darkTrackingRepository.findById(trackingId(record.getIdentifier()));
        DarkTrackingRecord existingRecord = existing.orElse(null);
        if (canSkipWithoutPreparingMetadata(record, existingRecord)) {
            pageSkippedUnchanged++;
            runSkippedUnchanged++;
            logger.debug("DARK stage skipping record {} because confirmed tracking hash is up to date for ARK {}",
                    record.getIdentifier(), existingRecord.getArk());
            return;
        }

        DarkStageCandidate candidate;
        try {
            candidate = prepareStageCandidate(record, existingRecord);
        } catch (Exception e) {
            logWarn("DARK stage failed preparing record " + record.getIdentifier() + ": " + e.getMessage());
            persistError(record.getIdentifier(), null, e.getMessage());
            return;
        }

        if (existingRecord == null || !existingRecord.hasArk()) {
            recordsToReserve.add(candidate);
            pageQueuedForReserve++;
            runQueuedForReserve++;
            logger.debug("DARK stage queued record {} for ARK reservation", candidate.getOaiId());
            return;
        }

        if (existingRecord.getState() == DarkTrackingState.RESERVED
                || existingRecord.getState() == DarkTrackingState.UPDATE
                || existingRecord.getState() == DarkTrackingState.ERROR) {
            recordsToStage.add(candidate);
            pageQueuedForStage++;
            runQueuedForStage++;
            logger.debug("DARK stage queued record {} for staging using tracked ARK {} in state {}",
                    candidate.getOaiId(), existingRecord.getArk(), existingRecord.getState());
            return;
        }

        if (!Objects.equals(existingRecord.getSourceMetadataHash(), candidate.getSourceMetadataHash())) {
            recordsToStage.add(candidate);
            pageQueuedForStage++;
            runQueuedForStage++;
            logger.debug("DARK stage queued record {} for update on ARK {} because payload changed", candidate.getOaiId(), existingRecord.getArk());
            return;
        }

        pageSkippedUnchanged++;
        runSkippedUnchanged++;
        logger.debug("DARK stage skipping record {} because tracking is up to date for ARK {}", candidate.getOaiId(), existingRecord.getArk());
    }

    private boolean canSkipWithoutPreparingMetadata(OAIRecord record, DarkTrackingRecord existingRecord) {
        if (existingRecord == null || !existingRecord.hasArk() || requiresStage(existingRecord.getState())) {
            return false;
        }
        String currentHash = record.getOriginalMetadataHash();
        return currentHash != null && Objects.equals(existingRecord.getSourceMetadataHash(), currentHash);
    }

    private boolean requiresStage(DarkTrackingState state) {
        return state == null
                || state == DarkTrackingState.RESERVED
                || state == DarkTrackingState.UPDATE
                || state == DarkTrackingState.ERROR;
    }

    private DarkStageCandidate prepareStageCandidate(OAIRecord record, DarkTrackingRecord existingRecord) throws Exception {
        String originalMetadata = metadataStore.getMetadata(snapshotMetadata, record.getOriginalMetadataHash());
        OAIRecordMetadata metadata = new OAIRecordMetadata(record.getIdentifier(), originalMetadata);
        String metadataForMinter = darkOriginalMetadataTransformerService.transformForMinter(
                record.getIdentifier(),
                record.getDatestamp(),
                originalMetadata,
                currentSourceMetadataSchema,
                currentTargetMetadataSchema);
        String targetUrl = urlExtractionService.extractBestUrl(metadata);
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalStateException("No target URL could be extracted");
        }

        Map<String, Object> minimalMetadata = level1MetadataService.buildMinimalMetadata(
                record.getIdentifier(),
                metadata,
                targetUrl);

        return DarkStageCandidate.builder()
                .oaiId(record.getIdentifier())
                .sourceMetadataHash(record.getOriginalMetadataHash())
                .targetUrl(targetUrl)
                .originalMetadata(metadataForMinter)
                .minimalMetadata(minimalMetadata)
                .existingRecord(existingRecord)
                .ark(existingRecord != null ? existingRecord.getArk() : null)
                .build();
    }

    @Override
    public void postPage() {
        reservePendingRecords();
        if (!pageHaltedBySystemicError) {
            stagePendingRecords(recordsToStage);
        }
        logInfo(String.format(
                "DARK stage page summary for network %s | processed=%s | queuedForReserve=%s | queuedForStage=%s | unchanged=%s | errors=%s | stageSuccesses=%s | reserveFailures=%s | stageFailures=%s",
                runningContext.getNetwork().getAcronym(),
                pageProcessed,
                pageQueuedForReserve,
                pageQueuedForStage,
                pageSkippedUnchanged,
                pageErrors,
                pageStageSuccesses,
                pageReserveFailures,
                pageStageFailures));
    }

    @Override
    protected void postRun() {
        if (!recordsToReserve.isEmpty() || !recordsToStage.isEmpty()) {
            postPage();
        }
        logInfo(String.format(
                "DARK stage run summary for network %s | processed=%s | queuedForReserve=%s | queuedForStage=%s | unchanged=%s | errors=%s | stageSuccesses=%s | reserveFailures=%s | stageFailures=%s",
                runningContext.getNetwork().getAcronym(),
                runProcessed,
                runQueuedForReserve,
                runQueuedForStage,
                runSkippedUnchanged,
                runErrors,
                runStageSuccesses,
                runReserveFailures,
                runStageFailures));
        if (snapshotMetadata != null) {
            catalogDatabaseManager.closeDataSource(snapshotMetadata.getSnapshotId());
        }
    }

    @Override
    protected boolean shouldSuppressPageExceptionStackTrace(Exception e) {
        return e instanceof DarkMinterClientException;
    }

    @Override
    protected void onPageFailure(Exception e) {
        logError("DARK stage failed on page " + getActualPage() + ": " + e.getMessage());
        if (snapshotMetadata != null) {
            catalogDatabaseManager.closeDataSource(snapshotMetadata.getSnapshotId());
        }
    }

    private void reservePendingRecords() {
        if (recordsToReserve.isEmpty()) {
            return;
        }

        for (List<DarkStageCandidate> chunk : chunks(recordsToReserve, darkProperties.getReserveBatchSize())) {
            if (pageHaltedBySystemicError) {
                break;
            }
            logInfo("DARK stage reserving " + chunk.size() + " ARKs for network " + runningContext.getNetwork().getAcronym());
            ReserveBatchResponse response;
            try {
                response = darkMinterClient.reserveBatch(
                        darkProperties.getAuthorityId(),
                        currentArkNaan,
                        chunk.stream().map(DarkStageCandidate::getOaiId).toList());
            } catch (DarkMinterClientException e) {
                handleReservationMinterFailure(e);
                break;
            }
            failOnSystemicReservationFailure(response, chunk.size());

            Map<String, ARKResponse> resultsByItemId = new HashMap<>();
            for (ARKResponse result : response.getResults()) {
                if (result.getClientItemId() != null) {
                    resultsByItemId.put(result.getClientItemId(), result);
                }
            }

            for (ReserveBatchResponse.BatchError error : response.getErrors()) {
                logWarn("DARK stage reservation error for record " + error.getClientItemId() + ": " + error.getError());
                persistError(error.getClientItemId(), null, error.getError());
            }

            List<DarkStageCandidate> reservedCandidates = new ArrayList<>();
            for (DarkStageCandidate candidate : chunk) {
                ARKResponse result = resultsByItemId.get(candidate.getOaiId());
                if (result == null) {
                    logWarn("DARK stage reservation returned no ARK for record " + candidate.getOaiId());
                    persistError(candidate.getOaiId(), null, "ARK reservation did not return a result for the record");
                    continue;
                }
                DarkStageCandidate reservedCandidate = candidate.withArk(result.getArk());
                persistReserved(reservedCandidate);
                logger.debug("DARK stage reserved ARK {} for record {}", result.getArk(), candidate.getOaiId());
                reservedCandidates.add(reservedCandidate);
            }

            stagePendingRecords(reservedCandidates);
            if (pageHaltedBySystemicError) {
                break;
            }
        }
        recordsToReserve.clear();
    }

    private void handleReservationMinterFailure(DarkMinterClientException e) {
        if (e.isRetryable()) {
            logError("DARK stage stopping because dARK minter returned a retryable error while reserving ARKs"
                    + " | errorCode=" + e.getErrorCode() + ": " + e.getMessage());
            pageHaltedBySystemicError = true;
            stop();
            return;
        }

        logError("DARK stage stopping because dARK minter returned a non-retryable error while reserving ARKs"
                + " | errorCode=" + e.getErrorCode() + ": " + e.getMessage());
        throw e;
    }

    private void failOnSystemicReservationFailure(ReserveBatchResponse response, int requestedCount) {
        if (response.getErrors() == null || response.getErrors().isEmpty()) {
            return;
        }

        int resultCount = response.getResults() == null ? 0 : response.getResults().size();
        if (resultCount > 0 || response.getErrors().size() < requestedCount) {
            return;
        }

        Optional<String> systemicError = response.getErrors().stream()
                .map(ReserveBatchResponse.BatchError::getError)
                .filter(DarkMinterClientException::isSystemicMessage)
                .findFirst();

        if (systemicError.isPresent()) {
            throw new DarkMinterClientException(
                    500,
                    "BATCH_RESERVATION_FAILED",
                    true,
                    "dARK minter reservation batch failed before allocating ARKs: " + systemicError.get());
        }
    }

    private void stagePendingRecords(List<DarkStageCandidate> candidates) {
        if (candidates.isEmpty()) {
            return;
        }

        logInfo("DARK stage staging " + candidates.size() + " records for network " + runningContext.getNetwork().getAcronym());
        for (DarkStageCandidate candidate : candidates) {
            String ark = candidate.getArk();
            try {
                StageArkRequest request = StageArkRequest.builder()
                        .authorityId(darkProperties.getAuthorityId())
                        .target(candidate.getTargetUrl())
                        .minimalMetadata(candidate.getMinimalMetadata())
                        .originalMetadata(candidate.getOriginalMetadata())
                        .metadataSchema(darkProperties.getMetadataSchema())
                        .metadataMediaType(darkProperties.getMetadataMediaType())
                        .build();

                logger.debug("DARK stage sending metadata for record {} to ARK {}", candidate.getOaiId(), ark);
                ARKResponse response = darkMinterClient.stageArk(ark, request);
                persistSuccess(candidate, response);
            } catch (DarkMinterClientException e) {
                if (e.isRetryable()) {
                    logError("DARK stage stopping because dARK minter returned a retryable error while staging record "
                            + candidate.getOaiId() + " | errorCode=" + e.getErrorCode() + ": " + e.getMessage());
                    persistRetryableStageFailure(candidate, e.getMessage());
                    pageHaltedBySystemicError = true;
                    stop();
                    return;
                }
                if (e.isSystemic()) {
                    logError("DARK stage stopping because dARK minter returned a non-retryable systemic error while staging record "
                            + candidate.getOaiId() + " | errorCode=" + e.getErrorCode() + ": " + e.getMessage());
                    if (candidate.getExistingRecord() == null || !candidate.getExistingRecord().hasArk()) {
                        persistReservedFailure(candidate, e.getMessage());
                    }
                    pageHaltedBySystemicError = true;
                    stop();
                    return;
                }
                if (candidate.getExistingRecord() == null || !candidate.getExistingRecord().hasArk()) {
                    logWarn("DARK stage failed after reserving ARK " + ark + " for record " + candidate.getOaiId() + ": " + e.getMessage());
                    persistReservedFailure(candidate, e.getMessage());
                } else {
                    logWarn("DARK stage failed updating ARK " + ark + " for record " + candidate.getOaiId() + ": " + e.getMessage());
                    persistError(candidate.getOaiId(), ark, e.getMessage());
                }
            }
        }
        candidates.clear();
    }

    private void persistReserved(DarkStageCandidate candidate) {
        DarkTrackingRecord record = darkTrackingRepository.findById(trackingId(candidate.getOaiId()))
                .orElseGet(DarkTrackingRecord::new);
        record.setArkNaan(currentArkNaan);
        record.setOaiId(candidate.getOaiId());
        record.setArk(candidate.getArk());
        record.setState(DarkTrackingState.RESERVED);
        record.setLastError(null);
        darkTrackingRepository.save(record);
    }

    private void persistSuccess(DarkStageCandidate candidate, ARKResponse response) {
        DarkTrackingRecord record = darkTrackingRepository.findById(trackingId(candidate.getOaiId()))
                .orElseGet(DarkTrackingRecord::new);
        record.setArkNaan(currentArkNaan);
        record.setOaiId(candidate.getOaiId());
        record.setArk(response.getArk());
        record.setSourceMetadataHash(candidate.getSourceMetadataHash());
        record.setTargetUrl(candidate.getTargetUrl());
        record.setState(response.getState().toTrackingState());
        record.setLastError(null);
        record.setLastStagedAt(LocalDateTime.now());
        if (response.getState() == DarkRemoteState.PUBLISHED) {
            record.setPublishedAt(LocalDateTime.now());
        }
        darkTrackingRepository.save(record);
        pageStageSuccesses++;
        runStageSuccesses++;
        logger.debug("DARK stage stored tracking for record {} with ARK {} in state={}",
                candidate.getOaiId(),
                response.getArk(),
                record.getState().getValue());
    }

    private void persistReservedFailure(DarkStageCandidate candidate, String error) {
        DarkTrackingRecord record = darkTrackingRepository.findById(trackingId(candidate.getOaiId()))
                .orElseGet(DarkTrackingRecord::new);
        record.setArkNaan(currentArkNaan);
        record.setOaiId(candidate.getOaiId());
        record.setArk(candidate.getArk());
        record.setState(DarkTrackingState.ERROR);
        record.setLastError(error);
        darkTrackingRepository.save(record);
        pageReserveFailures++;
        runReserveFailures++;
    }

    private void persistRetryableStageFailure(DarkStageCandidate candidate, String error) {
        DarkTrackingRecord record = darkTrackingRepository.findById(trackingId(candidate.getOaiId()))
                .orElseGet(DarkTrackingRecord::new);
        record.setArkNaan(currentArkNaan);
        record.setOaiId(candidate.getOaiId());
        record.setArk(candidate.getArk());
        record.setState(retryableFailureState(candidate));
        record.setLastError(error);
        darkTrackingRepository.save(record);
        pageStageFailures++;
        runStageFailures++;
    }

    private DarkTrackingState retryableFailureState(DarkStageCandidate candidate) {
        DarkTrackingRecord existingRecord = candidate.getExistingRecord();
        if (existingRecord == null || !existingRecord.hasArk() || existingRecord.getState() == DarkTrackingState.RESERVED) {
            return DarkTrackingState.RESERVED;
        }
        if (existingRecord.getState() == DarkTrackingState.ERROR) {
            return DarkTrackingState.ERROR;
        }
        return DarkTrackingState.UPDATE;
    }

    private void persistError(String oaiId, String ark, String error) {
        DarkTrackingRecord record = darkTrackingRepository.findById(trackingId(oaiId))
                .orElseGet(DarkTrackingRecord::new);
        record.setArkNaan(currentArkNaan);
        record.setOaiId(oaiId);
        record.setArk(ark != null ? ark : record.getArk());
        record.setState(DarkTrackingState.ERROR);
        record.setLastError(error);
        darkTrackingRepository.save(record);
        pageErrors++;
        runErrors++;
        if (ark != null) {
            pageStageFailures++;
            runStageFailures++;
        }
    }

    private void validateConfiguration() {
        if (darkProperties.getAuthorityId() == null || darkProperties.getAuthorityId().isBlank()) {
            throw new IllegalStateException("dark.authority-id must be configured");
        }
        if (darkProperties.getMetadataSchema() == null || darkProperties.getMetadataSchema().isBlank()) {
            throw new IllegalStateException("dark.metadata.schema must be configured");
        }
    }

    private List<List<DarkStageCandidate>> chunks(List<DarkStageCandidate> input, int chunkSize) {
        List<List<DarkStageCandidate>> chunks = new ArrayList<>();
        if (input.isEmpty()) {
            return chunks;
        }
        int size = Math.max(chunkSize, 1);
        for (int start = 0; start < input.size(); start += size) {
            chunks.add(new ArrayList<>(input.subList(start, Math.min(start + size, input.size()))));
        }
        return chunks;
    }

    private void resetRunCounters() {
        runProcessed = 0;
        runQueuedForReserve = 0;
        runQueuedForStage = 0;
        runSkippedUnchanged = 0;
        runErrors = 0;
        runStageSuccesses = 0;
        runReserveFailures = 0;
        runStageFailures = 0;
    }

    private void logInfo(String message) {
        logger.info(message);
        addSnapshotLogEntry("INFO", message);
    }

    private void logWarn(String message) {
        logger.warn(message);
        addSnapshotLogEntry("WARN", message);
    }

    private void logError(String message) {
        logger.error(message);
        addSnapshotLogEntry("ERROR", message);
    }

    private void addSnapshotLogEntry(String level, String message) {
        if (snapshotLogService == null || snapshotMetadata == null || snapshotMetadata.getSnapshotId() == null) {
            return;
        }
        snapshotLogService.addEntry(snapshotMetadata.getSnapshotId(), level + ": " + message);
    }

    private DarkTrackingRecordId trackingId(String oaiId) {
        return DarkTrackingRecordId.of(currentArkNaan, oaiId);
    }
}
