package org.lareferencia.contrib.dark.services;

import org.lareferencia.contrib.dark.client.StageArkRequest;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.lareferencia.core.worker.validation.ITransformer;
import org.lareferencia.core.worker.validation.ValidationException;
import org.springframework.stereotype.Service;

/** Prepares the same minter request for a preview and a worker page. */
@Service
public class DarkStagePayloadService {
    private final DarkProperties properties;
    private final DarkOriginalMetadataTransformerService formats;
    private final UrlExtractionService urls;
    private final DarkLevel1MetadataService builtIn;
    private final DarkL1TransformerService profiles;

    public DarkStagePayloadService(DarkProperties properties, DarkOriginalMetadataTransformerService formats,
            UrlExtractionService urls, DarkLevel1MetadataService builtIn, DarkL1TransformerService profiles) {
        this.properties = properties;
        this.formats = formats;
        this.urls = urls;
        this.builtIn = builtIn;
        this.profiles = profiles;
    }

    public ITransformer loadProfile(Long id) {
        return profiles.load(id);
    }

    public StageArkRequest prepare(ITransformer profile, SnapshotMetadata snapshot, OAIRecord record,
            String original, String sourceSchema, String targetSchema) throws OAIRecordMetadataParseException, ValidationException {
        String id = record.getIdentifier();
        String level2 = formats.transformForMinter(id, record.getDatestamp(), original, sourceSchema, targetSchema);
        String xoai;
        if ("xoai".equalsIgnoreCase(sourceSchema)) {
            xoai = original;
        } else if ("xoai".equalsIgnoreCase(targetSchema)) {
            xoai = level2;
        } else if (profile != null) {
            xoai = formats.transformForMinter(id, record.getDatestamp(), original, sourceSchema, "xoai");
        } else {
            throw new IllegalStateException("Level 1 extraction requires XOAI source or target metadata schema"
                    + " (source=" + sourceSchema + ", target=" + targetSchema + ")");
        }

        var metadata = new OAIRecordMetadata(id, xoai);
        String target = urls.extractBestUrl(metadata);
        if (target == null || target.isBlank()) throw new IllegalStateException("No target URL could be extracted");
        var level1 = profile == null ? builtIn.buildMinimalMetadata(id, metadata, target)
                : profiles.transform(profile, snapshot, record, metadata, target);
        return StageArkRequest.builder()
                .authorityId(properties.getAuthorityId())
                .target(target)
                .minimalMetadata(level1)
                .originalMetadata(level2)
                .metadataSchema(targetSchema)
                .metadataMediaType(properties.getMetadataMediaType())
                .build();
    }
}
