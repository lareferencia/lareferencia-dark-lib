package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArkStatusBatchError {

    private String code;
    private String message;
    private boolean retryable;
}
