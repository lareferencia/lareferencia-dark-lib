package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter @Setter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
public class URLUpdateResponse implements MinterHttpResponse {

    private List<NotUpdatedPid> not_updated_pids = new ArrayList<>();
    private List<UpdatedPid> updated_pids = new ArrayList<>();

    @SneakyThrows
    public static URLUpdateResponse fromString(String body) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(body, URLUpdateResponse.class);
    }

    public Set<String> getUpdatedPidsRaw() {
        return updated_pids.stream().map(updatePidResponse -> updatePidResponse.dark_id)
                .collect(Collectors.toSet());
    }

    public Set<String> getNotUpdatedPidsRaw() {
        return not_updated_pids.stream().map(notUpdated -> notUpdated.dark_id)
                .collect(Collectors.toSet());
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdatedPid implements Serializable {

        private String ark_hash;
        private String dark_id;
        private String previous_url;
        private String tx_recipt;
        private String update_url;

    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NotUpdatedPid implements Serializable {

        private String dark_id;
        private String error;

    }
}
