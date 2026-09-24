package org.lareferencia.contrib.dark.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.contrib.dark.client.ARKResponse;
import org.lareferencia.contrib.dark.client.ArkStatusBatchError;
import org.lareferencia.contrib.dark.client.ArkStatusBatchResponse;
import org.lareferencia.contrib.dark.client.ArkStatusBatchResult;
import org.lareferencia.contrib.dark.client.DarkMinterClient;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.lareferencia.contrib.dark.client.DarkRemoteState;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.DarkNetworkSettingsResolver;
import org.lareferencia.contrib.dark.services.DarkErrorCodec;
import org.lareferencia.contrib.dark.services.DarkProperties;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DarkReconcileWorker extends BaseBatchWorker<DarkTrackingRecord, NetworkRunningContext> {

    private static final Logger logger = LogManager.getLogger(DarkReconcileWorker.class);
    private static final int MINTER_STATUS_BATCH_LIMIT = 100;

    @Autowired
    private DarkTrackingRepository darkTrackingRepository;

    @Autowired
    private DarkMinterClient darkMinterClient;

    @Autowired
    private DarkProperties darkProperties;

    @Autowired
    private DarkNetworkSettingsResolver darkNetworkSettingsResolver;

    private final DarkErrorCodec darkErrorCodec = new DarkErrorCodec();

    private String currentArkNaan;
    private int pageProcessed;
    private int pageSkippedWithoutArk;
    private int pagePublished;
    private int pageReserved;
    private int pageDraft;
    private int pageUpdated;
    private int pageTombstone;
    private int pageErrors;
    private int runProcessed;
    private int runSkippedWithoutArk;
    private int runPublished;
    private int runReserved;
    private int runDraft;
    private int runUpdated;
    private int runTombstone;
    private int runErrors;
    private long runInitialPending;
    private LocalDateTime runStartedAt;
    private Collection<DarkTrackingState> reconcileStates;
    private List<String> selectedOaiIds = List.of();
    private final List<DarkTrackingRecord> pageRecords = new ArrayList<>();

    @Override
    protected void preRun() {
        setPageSize(Math.min(darkProperties.getReconcilePageSize(), MINTER_STATUS_BATCH_LIMIT));
        currentArkNaan = darkNetworkSettingsResolver.resolveArkNaan(runningContext.getNetwork());
        resetRunCounters();
        runStartedAt = LocalDateTime.now();
        reconcileStates = EnumSet.of(
                DarkTrackingState.RESERVED,
                DarkTrackingState.DRAFT,
                DarkTrackingState.UPDATE,
                DarkTrackingState.ERROR);
        selectedOaiIds = runningContext instanceof DarkManualRunningContext manualContext
                ? manualContext.getOaiIds() : List.of();
        runInitialPending = selectedOaiIds.isEmpty()
                ? darkTrackingRepository.countByIdArkNaanAndArkIsNotNullAndStateIn(currentArkNaan, reconcileStates)
                : darkTrackingRepository.findByIdArkNaanAndIdOaiIdInAndArkIsNotNullAndStateIn(
                        currentArkNaan, selectedOaiIds, reconcileStates,
                        org.springframework.data.domain.PageRequest.of(0, selectedOaiIds.size())).getTotalElements();
        org.lareferencia.core.worker.IPaginator<DarkTrackingRecord> paginator = selectedOaiIds.isEmpty()
                ? new DarkTrackingPaginator(darkTrackingRepository, currentArkNaan, reconcileStates)
                : new SelectedDarkTrackingPaginator(darkTrackingRepository, currentArkNaan, selectedOaiIds, reconcileStates);
        paginator.setPageSize(getPageSize());
        setPaginator(paginator);
        logger.info(
                "DARK reconcile run configured for network {} | arkNaan={} | pageSize={} | initialPending={}",
                runningContext.getNetwork().getAcronym(),
                currentArkNaan,
                getPageSize(),
                runInitialPending);
    }

    @Override
    public void prePage() {
        pageRecords.clear();
        pageProcessed = 0;
        pageSkippedWithoutArk = 0;
        pagePublished = 0;
        pageReserved = 0;
        pageDraft = 0;
        pageUpdated = 0;
        pageTombstone = 0;
        pageErrors = 0;
    }

    @Override
    public void processItem(DarkTrackingRecord record) {
        pageProcessed++;
        runProcessed++;

        if (!record.hasArk()) {
            pageSkippedWithoutArk++;
            runSkippedWithoutArk++;
            return;
        }

        // Loaded historical rows may still use ark:/NAAN/name until Flyway has
        // applied the normalization migration. Keep the outbound batch and the
        // persisted tracking value canonical.
        record.setArk(record.getArk());
        pageRecords.add(record);
    }

    @Override
    protected boolean shouldSuppressPageExceptionStackTrace(Exception e) {
        return e instanceof DarkMinterClientException;
    }

    @Override
    public void postPage() {
        reconcilePageRecords();
        logger.debug(
                "DARK reconcile page summary for network {} | processed={} | skippedWithoutArk={} | published={} | reserved={} | draft={} | update={} | tombstone={} | errors={}",
                runningContext.getNetwork().getAcronym(),
                pageProcessed,
                pageSkippedWithoutArk,
                pagePublished,
                pageReserved,
                pageDraft,
                pageUpdated,
                pageTombstone,
                pageErrors);
    }

    private void reconcilePageRecords() {
        for (int start = 0; start < pageRecords.size(); start += MINTER_STATUS_BATCH_LIMIT) {
            List<DarkTrackingRecord> records = pageRecords.subList(
                    start, Math.min(start + MINTER_STATUS_BATCH_LIMIT, pageRecords.size()));
            ArkStatusBatchResponse response = darkMinterClient.getArkStatuses(
                    records.stream().map(DarkTrackingRecord::getArk).toList());
            validateBatchResponse(response, records);
            for (int index = 0; index < records.size(); index++) {
                applyBatchResult(records.get(index), response.getResults().get(index));
            }
        }
    }

    private void validateBatchResponse(ArkStatusBatchResponse response, List<DarkTrackingRecord> records) {
        if (response == null || !"v1".equals(response.getVersion()) || response.getResults() == null
                || response.getResults().size() != records.size()) {
            throw invalidBatchResponse("Expected version v1 and one result per requested ARK");
        }
        for (int index = 0; index < records.size(); index++) {
            ArkStatusBatchResult result = response.getResults().get(index);
            if (result == null || !Objects.equals(records.get(index).getArk(), result.getArk())
                    || (result.getStatus() == null) == (result.getError() == null)) {
                throw invalidBatchResponse("Malformed result at index " + index);
            }
            if (result.getStatus() != null && result.getStatus().getState() == null) {
                throw invalidBatchResponse("Missing remote state at index " + index);
            }
        }
    }

    private void applyBatchResult(DarkTrackingRecord record, ArkStatusBatchResult result) {
        if (result.getStatus() != null) {
            applyRemoteStatus(record, result.getStatus());
            return;
        }

        ArkStatusBatchError error = result.getError();
        if (error.isRetryable()) {
            throw new DarkMinterClientException(503, error.getCode(), true,
                    "Retryable batch status error for " + record.getArk() + ": " + error.getMessage());
        }

        logger.warn("Failed reconciling ARK {}: {}", record.getArk(), error.getMessage());
        record.setState(DarkTrackingState.ERROR);
        record.setLastError(darkErrorCodec.encode("REMOTE_PERMANENT", error.getCode(), "READ_REMOTE_STATE",
                null, false, error.getMessage(), Map.of("source", "status/batch")));
        record.setLastReconciledAt(LocalDateTime.now());
        darkTrackingRepository.save(record);
        pageErrors++;
        runErrors++;
    }

    private void applyRemoteStatus(DarkTrackingRecord record, ARKResponse response) {
        DarkRemoteState remoteState = response.getState();
        record.setState(remoteState.toTrackingState());
        record.setLastReconciledAt(LocalDateTime.now());
        record.setLastError(null);
        if (remoteState == DarkRemoteState.PUBLISHED && record.getPublishedAt() == null) {
            record.setPublishedAt(LocalDateTime.now());
        }
        darkTrackingRepository.save(record);
        incrementStateCounters(remoteState);
    }

    private DarkMinterClientException invalidBatchResponse(String message) {
        return new DarkMinterClientException(502, "INVALID_BATCH_RESPONSE", true, message);
    }

    @Override
    protected void postRun() {
        long remainingPending = selectedOaiIds.isEmpty()
                ? darkTrackingRepository.countByIdArkNaanAndArkIsNotNullAndStateIn(currentArkNaan, reconcileStates)
                : darkTrackingRepository.findByIdArkNaanAndIdOaiIdInAndArkIsNotNullAndStateIn(
                        currentArkNaan, selectedOaiIds, reconcileStates,
                        org.springframework.data.domain.PageRequest.of(0, selectedOaiIds.size())).getTotalElements();
        long durationSeconds = runStartedAt == null ? 0 : Duration.between(runStartedAt, LocalDateTime.now()).toSeconds();
        logger.info(
                "DARK reconcile run summary for network {} | processed={} | initialPending={} | remainingPending={} | durationSeconds={} | skippedWithoutArk={} | published={} | reserved={} | draft={} | update={} | tombstone={} | errors={}",
                runningContext.getNetwork().getAcronym(),
                runProcessed,
                runInitialPending,
                remainingPending,
                durationSeconds,
                runSkippedWithoutArk,
                runPublished,
                runReserved,
                runDraft,
                runUpdated,
                runTombstone,
                runErrors);
    }

    private void incrementStateCounters(DarkRemoteState remoteState) {
        switch (remoteState) {
            case PUBLISHED -> {
                pagePublished++;
                runPublished++;
            }
            case RESERVED -> {
                pageReserved++;
                runReserved++;
            }
            case DRAFT -> {
                pageDraft++;
                runDraft++;
            }
            case UPDATE -> {
                pageUpdated++;
                runUpdated++;
            }
            case TOMBSTONE -> {
                pageTombstone++;
                runTombstone++;
            }
        }
    }

    private void resetRunCounters() {
        runProcessed = 0;
        runSkippedWithoutArk = 0;
        runPublished = 0;
        runReserved = 0;
        runDraft = 0;
        runUpdated = 0;
        runTombstone = 0;
        runErrors = 0;
        runInitialPending = 0;
        runStartedAt = null;
        reconcileStates = null;
        selectedOaiIds = List.of();
    }

    @Override
    public String getStatus() {
        DarkManualProgress progress = getManualProgress();
        String total = runInitialPending > 0 ? "/" + runInitialPending + " ("
                + Math.min(100, (int) Math.round((progress.processed() * 100.0d) / runInitialPending)) + "%)" : "";
        return "phase=" + progress.phase() + " processed=" + progress.processed() + total + " succeeded="
                + progress.succeeded() + " skipped=" + progress.skipped() + " failed=" + progress.failed();
    }

    public DarkManualProgress getManualProgress() {
        return new DarkManualProgress("RECONCILE", runProcessed,
                runPublished + runReserved + runDraft + runUpdated + runTombstone,
                runSkippedWithoutArk, runErrors);
    }
}
