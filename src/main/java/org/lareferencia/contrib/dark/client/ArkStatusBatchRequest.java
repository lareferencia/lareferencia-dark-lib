package org.lareferencia.contrib.dark.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.lareferencia.contrib.dark.services.DarkArkIdentifier;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArkStatusBatchRequest {

    private List<String> arks;

    public static ArkStatusBatchRequest fromArks(List<String> arks) {
        return new ArkStatusBatchRequest(arks.stream().map(DarkArkIdentifier::normalize).toList());
    }
}
