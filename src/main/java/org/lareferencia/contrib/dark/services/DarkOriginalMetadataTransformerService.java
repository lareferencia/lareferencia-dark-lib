package org.lareferencia.contrib.dark.services;

import org.lareferencia.core.metadata.IMDFormatTransformer;
import org.lareferencia.core.metadata.MDFormatTranformationException;
import org.lareferencia.core.metadata.MDFormatTransformerService;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;
import org.lareferencia.core.util.date.DateHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DarkOriginalMetadataTransformerService {

    @Autowired
    private MDFormatTransformerService mdFormatTransformerService;

    public String transformForMinter(String oaiId, LocalDateTime datestamp, String originalMetadata, String sourceSchema, String targetSchema) {
        if (originalMetadata == null || originalMetadata.isBlank()) {
            throw new IllegalStateException("Cannot transform empty metadata payload for record " + oaiId);
        }
        if (sourceSchema == null || sourceSchema.isBlank()) {
            throw new IllegalStateException("Source metadata schema is not configured for record " + oaiId);
        }
        if (targetSchema == null || targetSchema.isBlank()) {
            throw new IllegalStateException("Target metadata schema is not configured for record " + oaiId);
        }
        if (sourceSchema.equals(targetSchema)) {
            return originalMetadata;
        }

        try {
            OAIRecordMetadata metadata = new OAIRecordMetadata(oaiId, originalMetadata);
            IMDFormatTransformer transformer = mdFormatTransformerService.getMDTransformer(sourceSchema, targetSchema);
            synchronized (transformer) {
                transformer.setParameter("identifier", oaiId == null ? "" : oaiId);
                transformer.setParameter("timestamp", datestamp == null ? "" : DateHelper.getDateTimeMachineString(datestamp));
                return transformer.transformToString(metadata.getDOMDocument());
            }
        } catch (OAIRecordMetadataParseException e) {
            throw new IllegalStateException("Invalid XML metadata for record " + oaiId + ": " + e.getMessage(), e);
        } catch (MDFormatTranformationException e) {
            throw new IllegalStateException(
                    "Error transforming metadata for record " + oaiId + " from " + sourceSchema + " to " + targetSchema
                            + ": " + e.getMessage(),
                    e);
        }
    }
}
