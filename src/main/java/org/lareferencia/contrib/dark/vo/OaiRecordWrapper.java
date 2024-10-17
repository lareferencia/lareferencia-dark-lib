package org.lareferencia.contrib.dark.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.core.metadata.OAIRecordMetadata;

@Getter @AllArgsConstructor
public class OaiRecordWrapper {

    private OAIRecord oaiRecord;
    private OAIRecordMetadata oaiRecordMetadata;
}
