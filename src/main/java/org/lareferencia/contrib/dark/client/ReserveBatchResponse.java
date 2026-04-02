package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReserveBatchResponse {

    private List<ARKResponse> results = new ArrayList<>();
    private List<BatchError> errors = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchError {

        @JsonProperty("client_item_id")
        private String clientItemId;
        private String error;
        private Integer index;
    }
}
