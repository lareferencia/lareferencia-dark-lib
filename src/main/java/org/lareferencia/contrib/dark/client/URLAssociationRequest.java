package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.lareferencia.contrib.dark.vo.OaiRecordWrapper;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

@Getter @Setter
public class URLAssociationRequest implements Serializable {


    private String dnam_pk;
    private List<OaiIdentifierToURL> items;

    public URLAssociationRequest(List<OaiRecordWrapper> recordsToProcess, String privateKey) {
        this.dnam_pk = privateKey;
        this.items = recordsToProcess.stream().map(oaiRecord -> new OaiIdentifierToURL(
                oaiRecord.getOaiRecordMetadata().getIdentifier(),
                oaiRecord.getOaiRecordMetadata().getFieldOcurrences("dc.identifier.*").stream()
                        .filter(identifier -> identifier.startsWith("http://") || identifier.startsWith("https://"))
                        .findFirst()
                            .get()))
                .collect(Collectors.toList());
    }

    @SneakyThrows
    public String asJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(this);
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class OaiIdentifierToURL implements Serializable {

        private String oai_id;
        private String url;

    }
}
