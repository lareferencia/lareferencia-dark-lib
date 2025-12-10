package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified request for DARK minter operations (registration and URL updates).
 */
@Getter
@Setter
@NoArgsConstructor
public class MinterRequest implements Serializable {

    /**
     * Operation type for the minter.
     */
    public enum Operation {
        REGISTER("/load"),
        UPDATE("/update");

        private final String endpoint;

        Operation(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getEndpoint() {
            return endpoint;
        }
    }

    @JsonIgnore
    private Operation operation;

    @JsonProperty("dnam_pk")
    private String privateKey;

    private List<MinterItem> items;

    private MinterRequest(Operation operation, String privateKey, List<MinterItem> items) {
        this.operation = operation;
        this.privateKey = privateKey;
        this.items = items;
    }

    /**
     * Creates a registration request (new PIDs).
     */
    public static MinterRequest forRegistration(List<String> oaiIds, List<String> urls, String privateKey) {
        List<MinterItem> items = zipToItems(oaiIds, urls, true);
        return new MinterRequest(Operation.REGISTER, privateKey, items);
    }

    /**
     * Creates an update request (existing PIDs).
     */
    public static MinterRequest forUpdate(List<String> darkIds, List<String> urls, String privateKey) {
        List<MinterItem> items = zipToItems(darkIds, urls, false);
        return new MinterRequest(Operation.UPDATE, privateKey, items);
    }

    private static List<MinterItem> zipToItems(List<String> identifiers, List<String> urls, boolean isRegistration) {
        if (identifiers.size() != urls.size()) {
            throw new IllegalArgumentException("Identifiers and URLs must have the same size");
        }
        return java.util.stream.IntStream.range(0, identifiers.size())
                .mapToObj(i -> new MinterItem(identifiers.get(i), urls.get(i), isRegistration))
                .collect(Collectors.toList());
    }

    public String getEndpoint() {
        return operation.getEndpoint();
    }

    @SneakyThrows
    public String toJson() {
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
    }

    /**
     * Item in a minter request.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class MinterItem implements Serializable {
        // For registration: oai_id, for update: dark_id
        @JsonProperty("oai_id")
        private String oaiId;

        @JsonProperty("dark_id")
        private String darkId;

        private String url;

        public MinterItem(String identifier, String url, boolean isRegistration) {
            if (isRegistration) {
                this.oaiId = identifier;
            } else {
                this.darkId = identifier;
            }
            this.url = url;
        }
    }
}
