package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.lareferencia.contrib.dark.vo.DarkBusinessObject;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

@Getter @Setter
public class URLUpdateRequest implements MinterHttpRequest {


    private String dnam_pk;
    private List<DarkIdentifierToURL> items;

    public URLUpdateRequest(List<DarkBusinessObject> recordsToProcess, String privateKey) {
        this.dnam_pk = privateKey;
        this.items = recordsToProcess.stream().map(oaiRecord -> new DarkIdentifierToURL(
                oaiRecord.getDarkIdFromTracking(),
                oaiRecord.getItemUrlFromCollectedMetadata()))
                .collect(Collectors.toList());
    }

    @SneakyThrows
    public String asJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(this);
    }

    @Override
    public String operation() {
        return "/update";
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class DarkIdentifierToURL implements Serializable {

        private String dark_id;
        private String url;

    }
}
