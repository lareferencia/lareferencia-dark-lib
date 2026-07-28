package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lareferencia.core.metadata.OAIRecordMetadata;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(List.of(
                Map.of("schema", "oai", "value", "oai:test:1"),
                Map.of("schema", "doi", "value", "10.1234/demo")), result.get("alternate_identifiers"));
        assertEquals(List.of("https://alt.example.org/resource/1"), result.get("alternate_urls"));
    }

    @Test
    @DisplayName("Read qualified XOAI creator fields from harvested XML")
    void readsQualifiedXoaiCreator() throws Exception {
        String xml = """
                <metadata>
                  <element name="dc">
                    <element name="title"><element name="none"><field name="value">Documento BR</field></element></element>
                    <element name="creator"><element name="none"><field name="value">Pessoa, Exemplo|||0000-0001</field></element></element>
                    <element name="date"><element name="issued"><field name="value">1897-04-03</field></element></element>
                    <element name="identifier">
                      <element name="doi"><field name="value">https://doi.org/10.5555/example</field></element>
                      <element name="uri"><field name="value">https://repository.example/item/1</field></element>
                    </element>
                  </element>
                </metadata>
                """;

        Map<String, Object> result = service.buildMinimalMetadata(
                "oai:test:xoai",
                new OAIRecordMetadata("oai:test:xoai", xml),
                "https://repository.example/item/1");

        assertEquals(List.of("Pessoa, Exemplo"), result.get("authors"));
        assertEquals(1897, result.get("year"));
        assertEquals(List.of(
                Map.of("schema", "oai", "value", "oai:test:xoai"),
                Map.of("schema", "doi", "value", "10.5555/example")), result.get("alternate_identifiers"));
        assertFalse(result.containsKey("alternate_urls"));
    }

    @Test
    @DisplayName("Use contributor author only when creator is absent")
    void fallsBackToContributorAuthorWithoutMixing() throws Exception {
        OAIRecordMetadata fallback = baseMetadata("oai:test:fallback");
        fallback.addFieldOcurrence("dc.contributor.author", "Autora Alternativa|||0000-0002");
        fallback.addFieldOcurrence("dc.contributor.editor", "Editor Ignorado");
        assertEquals(List.of("Autora Alternativa"),
                service.buildMinimalMetadata("oai:test:fallback", fallback, "https://example.org").get("authors"));

        OAIRecordMetadata preferred = baseMetadata("oai:test:preferred");
        preferred.addFieldOcurrence("dc.creator", "Creadora Principal");
        preferred.addFieldOcurrence("dc.contributor.author", "Autora Alternativa");
        assertEquals(List.of("Creadora Principal"),
                service.buildMinimalMetadata("oai:test:preferred", preferred, "https://example.org").get("authors"));
    }

    @Test
    @DisplayName("Apply date language abstract and persistent identifier precedence")
    void normalizesOptionalMetadata() throws Exception {
        OAIRecordMetadata metadata = baseMetadata("oai:test:normalized");
        metadata.addFieldOcurrence("dc.creator", "Ada");
        metadata.addFieldOcurrence("dc.date.available", "2025-01-01");
        metadata.addFieldOcurrence("dc.date.created", "2001");
        metadata.addFieldOcurrence("dc.date.issued", "1999-05-01");
        metadata.addFieldOcurrence("dc.language", "spa");
        metadata.addFieldOcurrence("dc.description.tableofcontents", "No es un resumen");
        metadata.addFieldOcurrence("dc.description", "Resumen general");
        metadata.addFieldOcurrence("dc.description.abstract", "Resumen preferido");
        metadata.addFieldOcurrence("dc.identifier", "https://hdl.handle.net/1234/5678");
        metadata.addFieldOcurrence("dc.identifier", "https://example.org/alternate");

        Map<String, Object> result = service.buildMinimalMetadata(
                "oai:test:normalized", metadata, "https://example.org/target");

        assertEquals(1999, result.get("year"));
        assertEquals("es", result.get("language"));
        assertEquals("Resumen preferido", result.get("abstract"));
        assertEquals(List.of(
                Map.of("schema", "oai", "value", "oai:test:normalized"),
                Map.of("schema", "handle", "value", "hdl:1234/5678")), result.get("alternate_identifiers"));
        assertEquals(List.of("https://example.org/alternate"), result.get("alternate_urls"));
    }

    @Test
    @DisplayName("Fail when required fields are missing")
    void failsWhenRequiredFieldsAreMissing() throws Exception {
        OAIRecordMetadata metadata = new OAIRecordMetadata("oai:test:2");
        metadata.addFieldOcurrence("dc.title", "Sin autores");

        assertThrows(IllegalArgumentException.class,
                () -> service.buildMinimalMetadata("oai:test:2", metadata, "https://example.org"));
    }

    private OAIRecordMetadata baseMetadata(String id) throws Exception {
        OAIRecordMetadata metadata = new OAIRecordMetadata(id);
        metadata.addFieldOcurrence("dc.title", "Título");
        metadata.addFieldOcurrence("dc.date", "2026");
        return metadata;
    }
}
