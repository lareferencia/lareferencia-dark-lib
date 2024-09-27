package org.lareferencia.contrib.dark.vo;

import lombok.Getter;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.core.metadata.OAIRecordMetadata;

import java.util.List;

@Getter
public class DarkRecord {

    private DarkId darkId;
    private OAIRecord oaiRecord;
    private OAIRecordMetadata oaiRecordMetadata;
    private String url;
    private List<String> authors;

    private String title;
    private String oaiIdentifier;
    private String year;


    public DarkRecord(OAIRecord oaiRecord, OAIRecordMetadata oaiRecordMetadata, DarkId darkId) {
        this.oaiRecord = oaiRecord;
        this.oaiIdentifier = oaiRecord.getIdentifier();
        this.oaiRecordMetadata = oaiRecordMetadata;
        this.darkId = darkId;
        this.url = oaiRecordMetadata.getFieldOcurrences("dc.identifier.*").stream().filter(identifier -> identifier.startsWith("http://") || identifier.startsWith("https://")).findFirst().get();

    }

}
