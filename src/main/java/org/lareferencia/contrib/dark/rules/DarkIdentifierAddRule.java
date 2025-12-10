package org.lareferencia.contrib.dark.rules;

import java.util.Optional;

import org.lareferencia.contrib.dark.domain.DarkIdentifier;
import org.lareferencia.contrib.dark.repositories.DarkIdentifierRepository;
import org.lareferencia.core.domain.IOAIRecord;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.worker.validation.AbstractTransformerRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Transformation rule that adds the DARK identifier to the metadata if it exists in the local repository.
 */
@Component
public class DarkIdentifierAddRule extends AbstractTransformerRule {

    public static final String DC_IDENTIFIER_DARK = "dc.identifier.dark";

    @Autowired
    private DarkIdentifierRepository darkIdentifierRepository;

    public DarkIdentifierAddRule() {
    }

    @Override
    public boolean transform(SnapshotMetadata snapshotMetadata, IOAIRecord record, OAIRecordMetadata metadata) {
        Optional<DarkIdentifier> darkOptional = darkIdentifierRepository.findByOaiId(record.getIdentifier());

        if (darkOptional.isPresent()) {
            metadata.addFieldOcurrence(DC_IDENTIFIER_DARK, darkOptional.get().getDarkId());
            return true;
        }

        return false;
    }
}
