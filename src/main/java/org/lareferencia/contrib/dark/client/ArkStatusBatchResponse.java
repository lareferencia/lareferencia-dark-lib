package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArkStatusBatchResponse {

    private String version;
    private List<ArkStatusBatchResult> results;
}
