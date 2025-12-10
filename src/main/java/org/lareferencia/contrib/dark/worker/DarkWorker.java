package org.lareferencia.contrib.dark.worker;


import lombok.SneakyThrows;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.domain.IOAIRecord;
import org.lareferencia.contrib.dark.client.DarkMinterClient;
import org.lareferencia.contrib.dark.client.MinterResponse;
import org.lareferencia.contrib.dark.domain.DarkCredential;
import org.lareferencia.contrib.dark.domain.DarkIdentifier;
import org.lareferencia.contrib.dark.domain.DarkRecordContext;
import org.lareferencia.contrib.dark.repositories.DarkCredentialRepository;
import org.lareferencia.contrib.dark.repositories.DarkIdentifierRepository;
import org.lareferencia.contrib.dark.services.UrlExtractionService;
import org.lareferencia.core.metadata.IMetadataStore;
import org.lareferencia.core.metadata.ISnapshotStore;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Worker that processes OAI records to register and manage DARK persistent identifiers.
 * <p>
 * This worker iterates through harvested records and:
 * <ul>
 *   <li>Registers new PIDs for records without a DARK identifier</li>
 *   <li>Updates URLs for records whose access URL has changed</li>
 *   <li>Recovers tracking information for records that already have a DARK ID in metadata</li>
 * </ul>
 */
public class DarkWorker extends BaseBatchWorker<IOAIRecord, NetworkRunningContext> {

    public static final String ARK_PREFIX = "ark:/";
    public static final String DC_IDENTIFIER_DARK = "dc.identifier.dark";

    private static final Logger logger = LogManager.getLogger(DarkWorker.class);

    private Long snapshotId;
    private SnapshotMetadata snapshotMetadata;
    private DarkCredential darkCredential;

    private final List<DarkRecordContext> recordsForRegistration = new ArrayList<>();
    private final List<DarkRecordContext> recordsForUrlUpdate = new ArrayList<>();

    @Autowired
    private DarkIdentifierRepository darkIdentifierRepository;

    @Autowired
    private DarkCredentialRepository darkCredentialRepository;

    @Autowired
    private IMetadataStore metadataStore;

    @Autowired
    private ISnapshotStore snapshotStore;

    @Autowired
    private DarkMinterClient darkMinterClient;

    @Autowired
    private UrlExtractionService urlExtractionService;

    public DarkWorker() {
        super();
    }

    @Override
    public void preRun() {
        snapshotId = snapshotStore.findLastGoodKnownSnapshot(runningContext.getNetwork());
        darkCredential = darkCredentialRepository.findByNetworkId(runningContext.getNetwork().getId())
                .orElseThrow(() -> new IllegalStateException("No DARK credential found for network: " + runningContext.getNetwork().getAcronym()));

        if (snapshotId != null) {
            snapshotMetadata = snapshotStore.getSnapshotMetadata(snapshotId);
            logger.debug("dARK PID processing for snapshot: {}", snapshotId);
        } else {
            logger.warn("No harvested snapshots found for: {}", runningContext.getNetwork().getAcronym());
            this.setPaginator(null);
            this.stop();
        }
    }

    @Override
    public void prePage() {
        recordsForRegistration.clear();
        recordsForUrlUpdate.clear();
    }

    @Override
    public void processItem(IOAIRecord oaiRecord) {
        try {
            String metadataString = metadataStore.getMetadata(snapshotMetadata, oaiRecord.getOriginalMetadataHash());
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

            DarkRecordContext context = new DarkRecordContext(oaiId, url, existing);

            // Classify by action needed
            if (!context.isTracked()) {
                recordsForRegistration.add(context);
            } else if (context.hasUrlChanged()) {
                recordsForUrlUpdate.add(context);
            }
            // else: NO_ACTION_NEEDED

        } catch (Exception e) {
            logger.error("Error processing record {}: {}", oaiRecord.getIdentifier(), e.getMessage(), e);
            throw new RuntimeException("Error during DARK processing", e);
        }
    }

    private String getDarkIdFromMetadata(OAIRecordMetadata metadata) {
        String darkId = metadata.getFieldValue(DC_IDENTIFIER_DARK);
        return (darkId != null && !darkId.trim().isEmpty()) ? darkId.trim() : null;
    }

    @SneakyThrows
    @Override
    public void postPage() {
        if (!recordsForRegistration.isEmpty()) {
            registerNewPids();
        }
        if (!recordsForUrlUpdate.isEmpty()) {
            updateExistingUrls();
        }
    }

    private void updateExistingUrls() {
        MinterResponse response = darkMinterClient.updateUrls(recordsForUrlUpdate, darkCredential.getPrivateKey());

        recordsForUrlUpdate.stream()
                .filter(ctx -> response.getUpdatedDarkIds().contains(ctx.getDarkId()))
                .forEach(ctx -> {
                    DarkIdentifier darkId = ctx.existing().orElseThrow();
                    darkId.setUrl(ctx.url());
                    darkId.setUpdated(LocalDateTime.now());
                    darkIdentifierRepository.save(darkId);
                });
    }

    private void registerNewPids() {
        MinterResponse response = darkMinterClient.registerPids(recordsForRegistration, darkCredential.getPrivateKey());

        response.getIngestedPids().forEach(ingestedPid -> {
            recordsForRegistration.stream()
                    .filter(ctx -> ctx.oaiId().equals(ingestedPid.getOaiId()))
                    .findFirst()
                    .ifPresent(ctx -> {
                        String ark = ARK_PREFIX + ingestedPid.getArk();
                        darkIdentifierRepository.save(new DarkIdentifier(ark, ingestedPid.getOaiId(), ctx.url()));
                    });
        });
    }

    @Override
    public void postRun() {
        // No cleanup needed
    }
}
