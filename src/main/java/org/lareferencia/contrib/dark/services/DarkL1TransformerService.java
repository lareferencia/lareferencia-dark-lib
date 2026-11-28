package org.lareferencia.contrib.dark.services;

import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.lareferencia.core.repository.jpa.TransformerRepository;
import org.lareferencia.core.service.validation.ValidationService;
import org.lareferencia.core.worker.validation.ITransformer;
import org.lareferencia.core.worker.validation.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class DarkL1TransformerService {
    private final TransformerRepository transformers;
    private final ValidationService validation;
    private final DarkLevel1MetadataService level1;

    public DarkL1TransformerService(TransformerRepository transformers, ValidationService validation,
            DarkLevel1MetadataService level1) {
        this.transformers = transformers;
        this.validation = validation;
        this.level1 = level1;
    }

    /** Materialize the ordered rules once per run while the persistent rule collection is available. */
    @Transactional(readOnly = true)
    public ITransformer load(Long id) {
        if (id == null) return null;
        var profile = transformers.findById(id)
                .orElseThrow(() -> new IllegalStateException("L1 transformer " + id + " was not found"));
        try {
            return validation.createTransformerFromModel(profile);
        } catch (ValidationException e) {
            throw new IllegalStateException("Could not load L1 transformer " + id + ": " + e.getMessage(), e);
        }
    }

    /** Applies rules to the caller's per-record working DOM; L2 remains the original XML string. */
    public Map<String, Object> transform(ITransformer transformer, SnapshotMetadata snapshot, OAIRecord record,
            OAIRecordMetadata workingMetadata, String targetUrl) throws ValidationException {
        // A rule may also rewrite the catalogue identifier.
        var workingRecord = new OAIRecord(record.getId(), record.getIdentifier(), record.getDatestamp(),
                record.getOriginalMetadataHash(), record.isDeleted());
        workingRecord.setChangeType(record.getChangeType());
        transformer.transform(snapshot, workingRecord, workingMetadata);
        return level1.readTransformedMetadata(workingMetadata, targetUrl);
    }
}
