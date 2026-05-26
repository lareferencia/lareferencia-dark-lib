package org.lareferencia.contrib.dark.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.contrib.dark.client.ARKResponse;
import org.lareferencia.contrib.dark.client.DarkMinterClient;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.lareferencia.contrib.dark.client.DarkRemoteState;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.DarkNetworkSettingsResolver;
import org.lareferencia.contrib.dark.services.DarkProperties;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;

public class DarkReconcileWorker extends BaseBatchWorker<DarkTrackingRecord, NetworkRunningContext> {

    private static final Logger logger = LogManager.getLogger(DarkReconcileWorker.class);

    @Autowired
    private DarkTrackingRepository darkTrackingRepository;

    @Autowired
    private DarkMinterClient darkMinterClient;

    @Autowired
    private DarkProperties darkProperties;

    @Autowired
    private DarkNetworkSettingsResolver darkNetworkSettingsResolver;

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

    @Override
    protected void preRun() {
        setPageSize(darkProperties.getReconcilePageSize());
        currentArkNaan = darkNetworkSettingsResolver.resolveArkNaan(runningContext.getNetwork());
        resetRunCounters();
        runStartedAt = LocalDateTime.now();
        reconcileStates = EnumSet.of(DarkTrackingState.DRAFT, DarkTrackingState.UPDATE, DarkTrackingState.ERROR);
        runInitialPending = darkTrackingRepository.countByIdArkNaanAndArkIsNotNullAndStateIn(
                currentArkNaan,
                reconcileStates);
        DarkTrackingPaginator paginator = new DarkTrackingPaginator(
                darkTrackingRepository,
                currentArkNaan,
                reconcileStates);
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

        try {
            ARKResponse response = darkMinterClient.getArk(record.getArk());
            DarkRemoteState remoteState = response.getState();

            record.setState(remoteState.toTrackingState());
            record.setLastReconciledAt(LocalDateTime.now());
            record.setLastError(null);

            if (remoteState == DarkRemoteState.PUBLISHED) {
                if (record.getPublishedAt() == null) {
                    record.setPublishedAt(LocalDateTime.now());
                }
            }

            darkTrackingRepository.save(record);
            incrementStateCounters(remoteState);
        } catch (DarkMinterClientException e) {
            if (e.isSystemic()) {
                throw e;
            }
            logger.warn("Failed reconciling ARK {}: {}", record.getArk(), e.getMessage());
            record.setState(DarkTrackingState.ERROR);
            record.setLastError(e.getMessage());
            record.setLastReconciledAt(LocalDateTime.now());
            darkTrackingRepository.save(record);
            pageErrors++;
            runErrors++;
        }
    }

    @Override
    protected boolean shouldSuppressPageExceptionStackTrace(Exception e) {
        return e instanceof DarkMinterClientException;
    }

    @Override
    public void postPage() {
        logger.info(
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

    @Override
    protected void postRun() {
        long remainingPending = darkTrackingRepository.countByIdArkNaanAndArkIsNotNullAndStateIn(
                currentArkNaan,
                reconcileStates);
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
    }
}
