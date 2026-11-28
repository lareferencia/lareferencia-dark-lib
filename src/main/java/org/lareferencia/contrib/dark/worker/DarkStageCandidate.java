package org.lareferencia.contrib.dark.worker;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.lareferencia.contrib.dark.client.StageArkRequest;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;

@Value
@Builder
class DarkStageCandidate {
    String oaiId;
    String sourceMetadataHash;
    String stagePayloadHash;
    StageArkRequest request;
    DarkTrackingRecord existingRecord;
    @With
    String ark;
}
