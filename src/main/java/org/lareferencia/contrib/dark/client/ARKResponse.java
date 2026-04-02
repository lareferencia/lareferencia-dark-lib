package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ARKResponse {

    private String ark;
    private DarkRemoteState state;
    private String target;

    @JsonProperty("metadata_cid")
    private String metadataCid;

    @JsonProperty("metadata_schema")
    private String metadataSchema;

    @JsonProperty("minimal_metadata")
    private Map<String, Object> minimalMetadata;

    @JsonProperty("level1_cid")
    private String level1Cid;

    @JsonProperty("level2_cid")
    private String level2Cid;

    @JsonProperty("client_item_id")
    private String clientItemId;
}
