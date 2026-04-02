package org.lareferencia.contrib.dark.rules;

import java.util.Optional;

import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecordId;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.DarkNetworkSettingsResolver;
import org.lareferencia.core.domain.IOAIRecord;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.worker.validation.AbstractTransformerRule;
import org.lareferencia.core.worker.validation.ValidatorRuleMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Transformation rule that adds the DARK identifier to the metadata if it exists in the local repository.
 */
@Component
@ValidatorRuleMeta(
        name = "Agregar identificador ARK publicado",
        help = "Agrega dc.identifier.dark con el ARK publicado cuando el tracking local ya está en estado PUBLISHED.")
public class DarkIdentifierAddRule extends AbstractTransformerRule {

    public static final String DC_IDENTIFIER_DARK = "dc.identifier.dark";

    @Autowired
    private DarkTrackingRepository darkTrackingRepository;

    @Autowired
    private DarkNetworkSettingsResolver darkNetworkSettingsResolver;

    public DarkIdentifierAddRule() {
    }

    @Override
    public boolean transform(SnapshotMetadata snapshotMetadata, IOAIRecord record, OAIRecordMetadata metadata) {
        if (snapshotMetadata == null || snapshotMetadata.getNetwork() == null) {
            return false;
        }

        String arkNaan;
        try {
            arkNaan = darkNetworkSettingsResolver.resolveArkNaan(
                    snapshotMetadata.getNetwork().getAttributes(),
                    snapshotMetadata.getNetwork().getAcronym() + "(id:" + snapshotMetadata.getNetwork().getId() + ")");
        } catch (IllegalStateException e) {
            return false;
        }

        Optional<DarkTrackingRecord> darkOptional = darkTrackingRepository.findById(
                DarkTrackingRecordId.of(arkNaan, record.getIdentifier()));

        if (darkOptional.isPresent()
                && darkOptional.get().getState() == DarkTrackingState.PUBLISHED
                && darkOptional.get().getArk() != null
                && !darkOptional.get().getArk().isBlank()) {
            String ark = darkOptional.get().getArk();
            if (metadata.getFieldOcurrences(DC_IDENTIFIER_DARK).contains(ark)) {
                return false;
            }
            metadata.addFieldOcurrence(DC_IDENTIFIER_DARK, ark);
            return true;
        }

        return false;
    }
}
