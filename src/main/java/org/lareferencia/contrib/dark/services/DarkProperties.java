package org.lareferencia.contrib.dark.services;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dark")
public class DarkProperties {

    private Minter minter = new Minter();
    private Metadata metadata = new Metadata();
    private String authorityId;
    private String authHeaderName = "X-Authority-Id";
    private int stagePageSize = 100;
    private int reserveBatchSize = 100;
    private int reconcilePageSize = 100;

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
        private int maxRetries = 3;
        private List<Long> backoffSeconds = new ArrayList<>(List.of(5L, 30L, 60L));

        public int getMaxRetries() {
            return Math.max(0, maxRetries);
        }

        public List<Long> getBackoffSeconds() {
            if (backoffSeconds == null || backoffSeconds.isEmpty()) {
                return List.of(5L, 30L, 60L);
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
}
