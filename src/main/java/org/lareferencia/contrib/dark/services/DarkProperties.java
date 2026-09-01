package org.lareferencia.contrib.dark.services;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dark")
public class DarkProperties {

    private Minter minter = new Minter();
    private Metadata metadata = new Metadata();
    private Stage stage = new Stage();
    private Reserve reserve = new Reserve();
    private Reconcile reconcile = new Reconcile();
    private String authorityId;
    private String authHeaderName = "X-Authority-Id";

    /** Applies the installation configuration loaded from the database. */
    public synchronized void applyRuntimeConfiguration(ObjectNode client, ObjectNode runtimeStage, ObjectNode runtimeReconcile) {
        if (client.has("baseUrl")) minter.setBaseUrl(client.path("baseUrl").asText());
        if (client.has("authorityId")) setAuthorityId(client.path("authorityId").isNull() ? null : client.path("authorityId").asText());
        if (client.has("authHeaderName")) setAuthHeaderName(client.path("authHeaderName").asText());
        if (client.has("maxRetries")) minter.getRetry().setMaxRetries(client.path("maxRetries").asInt());
        if (client.has("backoffSeconds") && client.path("backoffSeconds").isArray()) {
            List<Long> values = new ArrayList<>(); client.path("backoffSeconds").forEach(item -> values.add(item.asLong()));
            minter.getRetry().setBackoffSeconds(values);
        }
        if (runtimeStage.has("metadataSchema")) setMetadataSchema(runtimeStage.path("metadataSchema").asText());
        if (runtimeStage.has("metadataMediaType")) setMetadataMediaType(runtimeStage.path("metadataMediaType").asText());
        if (runtimeStage.has("pageSize")) setStagePageSize(runtimeStage.path("pageSize").asInt());
        if (runtimeStage.has("maxPagesPerRun")) setStageMaxPagesPerRun(runtimeStage.path("maxPagesPerRun").asInt());
        if (runtimeStage.has("reserveBatchSize")) setReserveBatchSize(runtimeStage.path("reserveBatchSize").asInt());
        if (runtimeReconcile.has("pageSize")) setReconcilePageSize(runtimeReconcile.path("pageSize").asInt());
    }

    public String getAuthorityId() {
        return normalize(authorityId);
    }

    public void setAuthorityId(String authorityId) {
        this.authorityId = normalize(authorityId);
    }

    public String getAuthHeaderName() {
        return normalize(authHeaderName);
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = normalize(authHeaderName);
    }

    @Getter
    @Setter
    public static class Minter {
        private String baseUrl = "http://localhost:8001";
        private Retry retry = new Retry();

        public String getBaseUrl() {
            return normalize(baseUrl);
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = normalize(baseUrl);
        }

        public Retry getRetry() {
            if (retry == null) {
                retry = new Retry();
            }
            return retry;
        }
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxRetries = 5;
        private List<Long> backoffSeconds = new ArrayList<>(List.of(5L, 30L, 60L, 180L, 300L));

        public int getMaxRetries() {
            return Math.max(0, maxRetries);
        }

        public List<Long> getBackoffSeconds() {
            if (backoffSeconds == null || backoffSeconds.isEmpty()) {
                return List.of(5L, 30L, 60L, 180L, 300L);
            }
            return backoffSeconds;
        }
    }

    @Getter
    @Setter
    public static class Metadata {
        private String schema = "dublin_core";
        private String mediaType = "application/xml";
    }

    @Getter
    @Setter
    public static class Stage {
        private int pageSize = 100;
        private int maxPagesPerRun = 0;
    }

    @Getter
    @Setter
    public static class Reserve {
        private int batchSize = 100;
    }

    @Getter
    @Setter
    public static class Reconcile {
        private int pageSize = 100;
    }

    public int getStagePageSize() {
        return stage != null ? Math.max(1, stage.getPageSize()) : 100;
    }

    public void setStagePageSize(int stagePageSize) {
        ensureStage().setPageSize(stagePageSize);
    }

    public int getStageMaxPagesPerRun() {
        return stage != null ? Math.max(0, stage.getMaxPagesPerRun()) : 0;
    }

    public void setStageMaxPagesPerRun(int maxPagesPerRun) {
        ensureStage().setMaxPagesPerRun(maxPagesPerRun);
    }

    public int getReserveBatchSize() {
        return reserve != null ? Math.max(1, reserve.getBatchSize()) : 100;
    }

    public void setReserveBatchSize(int reserveBatchSize) {
        if (reserve == null) {
            reserve = new Reserve();
        }
        reserve.setBatchSize(reserveBatchSize);
    }

    public int getReconcilePageSize() {
        return reconcile != null ? Math.max(1, reconcile.getPageSize()) : 100;
    }

    public void setReconcilePageSize(int reconcilePageSize) {
        if (reconcile == null) {
            reconcile = new Reconcile();
        }
        reconcile.setPageSize(reconcilePageSize);
    }

    public String getMetadataSchema() {
        return metadata != null ? normalize(metadata.getSchema()) : null;
    }

    public void setMetadataSchema(String metadataSchema) {
        if (metadata == null) {
            metadata = new Metadata();
        }
        metadata.setSchema(normalize(metadataSchema));
    }

    public String getMetadataMediaType() {
        return metadata != null ? normalize(metadata.getMediaType()) : null;
    }

    public void setMetadataMediaType(String metadataMediaType) {
        if (metadata == null) {
            metadata = new Metadata();
        }
        metadata.setMediaType(normalize(metadataMediaType));
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private Stage ensureStage() {
        if (stage == null) {
            stage = new Stage();
        }
        return stage;
    }
}
