package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.lareferencia.core.metadata.OAIRecordMetadata;

import java.io.Serializable;
import java.util.List;

@Getter @Setter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
public class URLAssociationResponse implements MinterHttpResponse {

    private List<OaiIdentifierToDarkId> ingested_pids;
    private String load_time;
    private String verify_time;
    private String wallet_addr;


    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class OaiIdentifierToDarkId implements Serializable {

        private String ark;
        private String ark_hash;
        private String oai_id;
        private String ark_url;
        private String requested_url;
        private String tx_recipt;

    }
}
