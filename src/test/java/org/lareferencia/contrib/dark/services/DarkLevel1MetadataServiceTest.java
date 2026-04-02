package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lareferencia.core.metadata.OAIRecordMetadata;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("DarkLevel1MetadataService tests")
class DarkLevel1MetadataServiceTest {

    private final DarkLevel1MetadataService service = new DarkLevel1MetadataService();

    @Test
    @DisplayName("Build canonical minimal metadata from original XML")
    void buildsCanonicalMetadata() throws Exception {
        OAIRecordMetadata metadata = new OAIRecordMetadata("oai:test:1");
        metadata.addFieldOcurrence("dc.title", "Documento demo");
        metadata.addFieldOcurrence("dc.creator", "Ada Lovelace");
        metadata.addFieldOcurrence("dc.creator", "Grace Hopper");
        metadata.addFieldOcurrence("dc.date", "2026-03-24");
        metadata.addFieldOcurrence("dc.publisher", "LA Referencia");
        metadata.addFieldOcurrence("dc.type", "article");
        metadata.addFieldOcurrence("dc.language", "es_AR");
        metadata.addFieldOcurrence("dc.description", "Resumen corto");
        metadata.addFieldOcurrence("dc.subject", "Open Science");
        metadata.addFieldOcurrence("dc.rights", "CC-BY");
        metadata.addFieldOcurrence("dc.identifier", "oai:test:1");
        metadata.addFieldOcurrence("dc.identifier", "10.1234/demo");
        metadata.addFieldOcurrence("dc.identifier", "https://example.org/resource/1");
        metadata.addFieldOcurrence("dc.identifier", "https://alt.example.org/resource/1");

        Map<String, Object> result = service.buildMinimalMetadata("oai:test:1", metadata, "https://example.org/resource/1");

        assertEquals("Documento demo", result.get("title"));
        assertEquals(List.of("Ada Lovelace", "Grace Hopper"), result.get("authors"));
        assertEquals(2026, result.get("year"));
        assertEquals("LA Referencia", result.get("publisher"));
        assertEquals("article", result.get("resource_type"));
        assertEquals("es", result.get("language"));
        assertEquals("Resumen corto", result.get("abstract"));
        assertEquals(List.of("Open Science"), result.get("subjects"));
        assertEquals("CC-BY", result.get("rights"));
        assertEquals(List.of("https://alt.example.org/resource/1"), result.get("alternate_urls"));
    }

    @Test
    @DisplayName("Fail when required fields are missing")
    void failsWhenRequiredFieldsAreMissing() throws Exception {
        OAIRecordMetadata metadata = new OAIRecordMetadata("oai:test:2");
        metadata.addFieldOcurrence("dc.title", "Sin autores");

        assertThrows(IllegalArgumentException.class,
                () -> service.buildMinimalMetadata("oai:test:2", metadata, "https://example.org"));
    }
}
