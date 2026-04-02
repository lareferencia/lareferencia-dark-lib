package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lareferencia.core.metadata.IMDFormatTransformer;
import org.lareferencia.core.metadata.MDFormatTranformationException;
import org.lareferencia.core.metadata.MDFormatTransformerService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DarkOriginalMetadataTransformerService tests")
class DarkOriginalMetadataTransformerServiceTest {

    @Mock
    private MDFormatTransformerService mdFormatTransformerService;

    @Mock
    private IMDFormatTransformer transformer;

    @InjectMocks
    private DarkOriginalMetadataTransformerService service;

    @Test
    @DisplayName("Returns original metadata when source and target schema are equal")
    void returnsOriginalWhenSchemasAreEqual() throws Exception {
        String original = "<metadata><element name=\"dc\"/></metadata>";

        String result = service.transformForMinter(
                "oai:test:1",
                LocalDateTime.of(2026, 4, 2, 10, 15, 0),
                original,
                "xoai",
                "xoai");

        assertEquals(original, result);
        verify(mdFormatTransformerService, never()).getMDTransformer(any(), any());
    }

    @Test
    @DisplayName("Transforms metadata using configured crosswalk when schemas differ")
    void transformsWhenSchemasDiffer() throws Exception {
        String original = "<metadata><element name=\"dc\"/></metadata>";
        LocalDateTime datestamp = LocalDateTime.of(2026, 4, 2, 10, 15, 0);
        when(mdFormatTransformerService.getMDTransformer("xoai", "dublin_core")).thenReturn(transformer);
        when(transformer.transformToString(any(Document.class))).thenReturn("<metadata><dc:title>Demo</dc:title></metadata>");

        String result = service.transformForMinter(
                "oai:test:2",
                datestamp,
                original,
                "xoai",
                "dublin_core");

        assertEquals("<metadata><dc:title>Demo</dc:title></metadata>", result);
        verify(transformer).setParameter("identifier", "oai:test:2");
        verify(transformer).setParameter("timestamp", "2026-04-02T10:15:00Z");
        verify(transformer).transformToString(any(Document.class));
    }

    @Test
    @DisplayName("Fails with clear error when no crosswalk exists between schemas")
    void failsWhenCrosswalkIsMissing() throws Exception {
        when(mdFormatTransformerService.getMDTransformer("xoai", "dublin_core"))
                .thenThrow(new MDFormatTranformationException("missing transformer"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.transformForMinter(
                "oai:test:3",
                LocalDateTime.of(2026, 4, 2, 10, 15, 0),
                "<metadata><element name=\"dc\"/></metadata>",
                "xoai",
                "dublin_core"));

        assertTrue(ex.getMessage().contains("from xoai to dublin_core"));
    }
}
