package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unified response from DARK minter operations.
 * Handles both registration and update responses.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinterResponse implements Serializable {

    // Registration response fields
    @JsonProperty("ingested_pids")
    private List<IngestedPid> ingestedPids = new ArrayList<>();

    @JsonProperty("load_time")
    private String loadTime;

    @JsonProperty("verify_time")
    private String verifyTime;

    @JsonProperty("wallet_addr")
    private String walletAddr;

    // Update response fields
    @JsonProperty("updated_pids")
    private List<UpdatedPid> updatedPids = new ArrayList<>();

    @JsonProperty("not_updated_pids")
    private List<NotUpdatedPid> notUpdatedPids = new ArrayList<>();

    /**
     * Gets the set of successfully updated DARK IDs.
     */
    public Set<String> getUpdatedDarkIds() {
        return updatedPids.stream()
                .map(UpdatedPid::getDarkId)
                .collect(Collectors.toSet());
    }

    /**
     * Deserializes a JSON response.
     */
    @SneakyThrows
    public static MinterResponse fromJson(String json) {
        return new ObjectMapper().readValue(json, MinterResponse.class);
    }

    /**
     * PID that was successfully registered.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IngestedPid implements Serializable {
        private String ark;

        @JsonProperty("ark_hash")
        private String arkHash;

        @JsonProperty("oai_id")
        private String oaiId;

        @JsonProperty("ark_url")
        private String arkUrl;

        @JsonProperty("requested_url")
        private String requestedUrl;

        @JsonProperty("tx_recipt")
        private String txReceipt;
    }

    /**
     * PID that was successfully updated.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdatedPid implements Serializable {
        @JsonProperty("ark_hash")
        private String arkHash;

        @JsonProperty("dark_id")
        private String darkId;

        @JsonProperty("previous_url")
        private String previousUrl;

        @JsonProperty("tx_recipt")
        private String txReceipt;

        @JsonProperty("update_url")
        private String updateUrl;
    }

    /**
     * PID that failed to update.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NotUpdatedPid implements Serializable {
        @JsonProperty("dark_id")
        private String darkId;

        private String error;
    }
}
