package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.lareferencia.contrib.dark.services.DarkArkIdentifier;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArkStatusBatchResult {

    private String ark;
    private ARKResponse status;
    private ArkStatusBatchError error;

    public void setArk(String ark) {
        this.ark = DarkArkIdentifier.normalize(ark);
    }
}
