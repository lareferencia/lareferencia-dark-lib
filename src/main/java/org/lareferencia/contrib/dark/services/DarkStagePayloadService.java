package org.lareferencia.contrib.dark.services;

import org.lareferencia.contrib.dark.client.StageArkRequest;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.lareferencia.core.worker.validation.ITransformer;
import org.lareferencia.core.worker.validation.ValidationException;
import org.springframework.stereotype.Service;

/** Shared deposit preparation for previews, collection evaluations and submissions. */
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

    record DepositFormat(String source, String target, String mediaType) {}

    DepositFormat depositFormat(ITransformer profile, String source, String target) {
        if (source == null || source.isBlank() || target == null || target.isBlank() || target.codePointCount(0, target.length()) > 50) {
            throw new IllegalStateException("A source schema and a deposit schema of up to 50 characters are required");
        }
        String mediaType = properties.getMetadataMediaType();
        String baseType = mediaType == null ? "" : mediaType.split(";", 2)[0].trim();
        if (baseType.isBlank() || baseType.codePointCount(0, baseType.length()) > 255) {
            throw new IllegalStateException("The base MIME type must contain 1 to 255 characters");
        }
        if (profile == null && !"xoai".equalsIgnoreCase(source) && !"xoai".equalsIgnoreCase(target)) {
            throw new IllegalStateException("Level 1 extraction requires XOAI source or target metadata schema"
                    + " (source=" + source + ", target=" + target + ")");
        }
        return new DepositFormat(source, target, mediaType);
    }

    public StageArkRequest prepare(ITransformer profile, SnapshotMetadata snapshot, OAIRecord record,
            String original, String sourceSchema, String targetSchema) throws OAIRecordMetadataParseException, ValidationException {
        return prepare(profile, snapshot, record, original, depositFormat(profile, sourceSchema, targetSchema));
    }

    StageArkRequest prepare(ITransformer profile, SnapshotMetadata snapshot, OAIRecord record, String original,
            DepositFormat format) throws OAIRecordMetadataParseException, ValidationException {
        String id = record.getIdentifier();
        String sourceSchema = format.source();
        String targetSchema = format.target();
        String level2 = formats.transformForMinter(id, record.getDatestamp(), original, sourceSchema, targetSchema);
        if (level2 == null || level2.isBlank()) {
            throw new DarkPreparationException(DarkPreparationException.Reason.TRANSFORMATION, "Empty deposit metadata");
        }
        String xoai;
        if ("xoai".equalsIgnoreCase(sourceSchema)) {
            xoai = original;
        } else if ("xoai".equalsIgnoreCase(targetSchema)) {
            xoai = level2;
        } else {
            xoai = formats.transformForMinter(id, record.getDatestamp(), original, sourceSchema, "xoai");
        }

        var metadata = new OAIRecordMetadata(id, xoai);
        String target = urls.extractBestUrl(metadata);
        if (target == null || target.isBlank()) throw new DarkPreparationException(DarkPreparationException.Reason.MISSING_TARGET, "No target URL could be extracted");
        var level1 = profile == null ? builtIn.buildMinimalMetadata(id, metadata, target)
                : profiles.transform(profile, snapshot, record, metadata, target);
        return StageArkRequest.builder()
                .authorityId(properties.getAuthorityId())
                .target(target)
                .minimalMetadata(level1)
                .originalMetadata(level2)
                .metadataSchema(targetSchema)
                .metadataMediaType(format.mediaType())
                .build();
    }
}
