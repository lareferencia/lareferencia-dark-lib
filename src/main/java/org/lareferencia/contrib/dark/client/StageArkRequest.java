package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StageArkRequest {

    @JsonProperty("authority_id")
    private String authorityId;

    private String target;

    @JsonProperty("minimal_metadata")
    private Map<String, Object> minimalMetadata;

    @JsonProperty("original_metadata")
    private String originalMetadata;

    @JsonProperty("metadata_schema")
    private String metadataSchema;

    @JsonProperty("metadata_media_type")
    private String metadataMediaType;
}
