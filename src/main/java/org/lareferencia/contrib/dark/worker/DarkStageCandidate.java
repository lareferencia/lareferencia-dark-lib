package org.lareferencia.contrib.dark.worker;

import lombok.Builder;
import lombok.Value;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;

import java.util.Map;

@Value
@Builder
class DarkStageCandidate {
    String oaiId;
    String sourceMetadataHash;
    String targetUrl;
    String originalMetadata;
    Map<String, Object> minimalMetadata;
    DarkTrackingRecord existingRecord;
    String ark;

    DarkStageCandidate withArk(String newArk) {
        return DarkStageCandidate.builder()
                .oaiId(oaiId)
                .sourceMetadataHash(sourceMetadataHash)
                .targetUrl(targetUrl)
                .originalMetadata(originalMetadata)
                .minimalMetadata(minimalMetadata)
                .existingRecord(existingRecord)
                .ark(newArk)
                .build();
    }
}
