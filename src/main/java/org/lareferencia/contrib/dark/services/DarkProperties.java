package org.lareferencia.contrib.dark.services;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    @Getter
    @Setter
    public static class Minter {
        private String baseUrl = "http://localhost:8001";
    }

    @Getter
    @Setter
    public static class Metadata {
        private String schema = "dublin_core";
        private String mediaType = "application/xml";
    }

    public String getMetadataSchema() {
        return metadata != null ? metadata.getSchema() : null;
    }

    public void setMetadataSchema(String metadataSchema) {
        if (metadata == null) {
            metadata = new Metadata();
        }
        metadata.setSchema(metadataSchema);
    }

    public String getMetadataMediaType() {
        return metadata != null ? metadata.getMediaType() : null;
    }

    public void setMetadataMediaType(String metadataMediaType) {
        if (metadata == null) {
            metadata = new Metadata();
        }
        metadata.setMediaType(metadataMediaType);
    }
}
