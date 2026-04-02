package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lareferencia.core.metadata.OAIRecordMetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("UrlExtractionService tests")
class UrlExtractionServiceTest {

    private final UrlExtractionService service = new UrlExtractionService();

    @Test
    @DisplayName("Prefer DOI URL when present")
    void prefersDoi() throws Exception {
        OAIRecordMetadata metadata = new OAIRecordMetadata("oai:test:1");
        metadata.addFieldOcurrence("dc.identifier", "https://repository.example.org/item/1");
        metadata.addFieldOcurrence("dc.identifier", "https://doi.org/10.1234/example");

        assertEquals("https://doi.org/10.1234/example", service.extractBestUrl(metadata));
    }

    @Test
    @DisplayName("Prefer Handle URL before longest URL")
    void prefersHandle() throws Exception {
        OAIRecordMetadata metadata = new OAIRecordMetadata("oai:test:2");
        metadata.addFieldOcurrence("dc.identifier", "https://repository.example.org/very/long/path");
        metadata.addFieldOcurrence("dc.identifier", "https://hdl.handle.net/12345/abc");

        assertEquals("https://hdl.handle.net/12345/abc", service.extractBestUrl(metadata));
    }

    @Test
    @DisplayName("Fallback to longest URL when DOI and Handle are absent")
    void fallsBackToLongestUrl() throws Exception {
        OAIRecordMetadata metadata = new OAIRecordMetadata("oai:test:3");
        metadata.addFieldOcurrence("dc.identifier", "https://short.example.org");
        metadata.addFieldOcurrence("dc.identifier", "https://repository.example.org/resources/document/123");

        assertEquals("https://repository.example.org/resources/document/123", service.extractBestUrl(metadata));
    }
}
