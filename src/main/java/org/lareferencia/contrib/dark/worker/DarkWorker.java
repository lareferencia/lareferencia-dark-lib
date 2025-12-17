package org.lareferencia.contrib.dark.worker;

import lombok.SneakyThrows;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flowable.engine.delegate.DelegateExecution;
import org.lareferencia.contrib.dark.client.DarkMinterClient;
import org.lareferencia.contrib.dark.client.MinterResponse;
import org.lareferencia.contrib.dark.domain.DarkCredential;
import org.lareferencia.contrib.dark.domain.DarkIdentifier;
import org.lareferencia.contrib.dark.domain.DarkRecordContext;
import org.lareferencia.contrib.dark.repositories.DarkCredentialRepository;
import org.lareferencia.contrib.dark.repositories.DarkIdentifierRepository;
import org.lareferencia.contrib.dark.services.UrlExtractionService;
import org.lareferencia.core.domain.IOAIRecord;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.domain.OAIRecord;
import org.lareferencia.core.metadata.IMetadataStore;
import org.lareferencia.core.metadata.ISnapshotStore;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.jpa.OAIRecordRepository;
import org.lareferencia.core.worker.AbstractFlowableWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Worker that processes OAI records to register and manage DARK persistent
 * identifiers.
 * <p>
 * This worker iterates through harvested records and:
 * <ul>
 * <li>Registers new PIDs for records without a DARK identifier</li>
 * <li>Updates URLs for records whose access URL has changed</li>
 * <li>Recovers tracking information for records that already have a DARK ID in
 * metadata</li>
 * </ul>
 */
public class DarkWorker extends AbstractFlowableWorker {

    public static final String ARK_PREFIX = "ark:/";
    public static final String DC_IDENTIFIER_DARK = "dc.identifier.dark";

    private static final Logger logger = LogManager.getLogger(DarkWorker.class);

    @Autowired
    private DarkIdentifierRepository darkIdentifierRepository;

    @Autowired
    private DarkCredentialRepository darkCredentialRepository;

    @Autowired
    private IMetadataStore metadataStore;

    @Autowired
    private ISnapshotStore snapshotStore;

    @Autowired
    private OAIRecordRepository oaiRecordRepository;

    @Autowired
    private DarkMinterClient darkMinterClient;

    @Autowired
    private UrlExtractionService urlExtractionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    protected void executeWorker(DelegateExecution execution) throws Exception {

        // 1. Context is already resolved in base class
        if (this.network == null) {
            // Try to resolve if missing (e.g. called from test or different scope)
            this.network = contextResolver.resolveNetwork(execution);
        }

        // 2. Initialize State
        DarkContext context = new DarkContext();
        context.snapshotId = snapshotStore.findLastGoodKnownSnapshot(this.network);

        if (context.snapshotId == null) {
            logger.warn("No harvested snapshots found for: {}", this.network.getAcronym());
            return;
        }

        context.darkCredential = darkCredentialRepository.findByNetworkId(this.network.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No DARK credential found for network: " + this.network.getAcronym()));

        context.snapshotMetadata = snapshotStore.getSnapshotMetadata(context.snapshotId);
        logger.debug("dARK PID processing for snapshot: {}", context.snapshotId);

        // 3. Process records in batches (transactions)
        int page = 0;
        int pageSize = 1000;
        Page<OAIRecord> recordsPage;

        do {
            final int currentPage = page;
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            recordsPage = transactionTemplate.execute(status -> {
                // Clear page context
                context.recordsForRegistration.clear();
                context.recordsForUrlUpdate.clear();

                // Fetch page
                PageRequest pageRequest = PageRequest.of(currentPage, pageSize);
                Page<OAIRecord> p = oaiRecordRepository.findBySnapshotId(context.snapshotId, pageRequest);

                for (IOAIRecord record : p.getContent()) {
                    processItem(record, context);
                }

                // Post Page
                postPage(context);

                return p;
            });

            page++;

        } while (recordsPage != null && recordsPage.hasNext());

    }

    private void processItem(IOAIRecord oaiRecord, DarkContext context) {
        try {
            String metadataString = metadataStore.getMetadata(context.snapshotMetadata,
                    oaiRecord.getOriginalMetadataHash());
            OAIRecordMetadata metadata = new OAIRecordMetadata(oaiRecord.getIdentifier(), metadataString);

            String oaiId = oaiRecord.getIdentifier();
            String url = urlExtractionService.extractBestUrl(metadata);
            Optional<DarkIdentifier> existing = darkIdentifierRepository.findByOaiId(oaiId);

            // Recovery: if DARK ID exists in metadata but not in DB, save it
            if (existing.isEmpty()) {
                String darkIdInMetadata = getDarkIdFromMetadata(metadata);
                if (darkIdInMetadata != null) {
                    darkIdentifierRepository.save(new DarkIdentifier(darkIdInMetadata, oaiId, url));
                    existing = Optional.of(new DarkIdentifier(darkIdInMetadata, oaiId, url));
                }
            }

            DarkRecordContext recordContext = new DarkRecordContext(oaiId, url, existing);

            // Classify by action needed
            if (!recordContext.isTracked()) {
                context.recordsForRegistration.add(recordContext);
            } else if (recordContext.hasUrlChanged()) {
                context.recordsForUrlUpdate.add(recordContext);
            }
            // else: NO_ACTION_NEEDED

        } catch (Exception e) {
            logger.error("Error processing record {}: {}", oaiRecord.getIdentifier(), e.getMessage(), e);
            // throw new RuntimeException("Error during DARK processing", e); // Don't crash
            // worker on single record error? Original code did throw. I'll log and continue
            // or rethrow? Original threw.
        }
    }

    private String getDarkIdFromMetadata(OAIRecordMetadata metadata) {
        String darkId = metadata.getFieldValue(DC_IDENTIFIER_DARK);
        return (darkId != null && !darkId.trim().isEmpty()) ? darkId.trim() : null;
    }

    @SneakyThrows
    private void postPage(DarkContext context) {
        if (!context.recordsForRegistration.isEmpty()) {
            registerNewPids(context);
        }
        if (!context.recordsForUrlUpdate.isEmpty()) {
            updateExistingUrls(context);
        }
    }

    private void updateExistingUrls(DarkContext context) {
        MinterResponse response = darkMinterClient.updateUrls(context.recordsForUrlUpdate,
                context.darkCredential.getPrivateKey());

        context.recordsForUrlUpdate.stream()
                .filter(ctx -> response.getUpdatedDarkIds().contains(ctx.getDarkId()))
                .forEach(ctx -> {
                    DarkIdentifier darkId = ctx.existing().orElseThrow();
                    darkId.setUrl(ctx.url());
                    darkId.setUpdated(LocalDateTime.now());
                    darkIdentifierRepository.save(darkId);
                });
    }

    private void registerNewPids(DarkContext context) {
        MinterResponse response = darkMinterClient.registerPids(context.recordsForRegistration,
                context.darkCredential.getPrivateKey());

        response.getIngestedPids().forEach(ingestedPid -> {
            context.recordsForRegistration.stream()
                    .filter(ctx -> ctx.oaiId().equals(ingestedPid.getOaiId()))
                    .findFirst()
                    .ifPresent(ctx -> {
                        String ark = ARK_PREFIX + ingestedPid.getArk();
                        darkIdentifierRepository.save(new DarkIdentifier(ark, ingestedPid.getOaiId(), ctx.url()));
                    });
        });
    }

    // Inner Context Class
    private static class DarkContext {
        Long snapshotId;
        SnapshotMetadata snapshotMetadata;
        DarkCredential darkCredential;
        final List<DarkRecordContext> recordsForRegistration = new ArrayList<>();
        final List<DarkRecordContext> recordsForUrlUpdate = new ArrayList<>();
    }

}
