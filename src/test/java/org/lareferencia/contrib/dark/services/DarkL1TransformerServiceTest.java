package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.Test;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.lareferencia.core.repository.jpa.TransformerRepository;
import org.lareferencia.core.service.validation.ValidationService;
import org.lareferencia.core.worker.validation.ITransformer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DarkL1TransformerServiceTest {
    private final DarkLevel1MetadataService reader = new DarkLevel1MetadataService();

    @Test
    void isolatesRuleChangesFromTheCatalogueRecordAndSourceXml() throws Exception {
        var service = new DarkL1TransformerService(mock(TransformerRepository.class),
                mock(ValidationService.class), reader);
        var transformer = mock(ITransformer.class);
        var record = OAIRecord.create("oai:1", null, "hash", false);
        var snapshot = new SnapshotMetadata();
        String xml = "<metadata><element name='dc'><element name='title'><field name='value'>Título</field></element></element></metadata>";
        when(transformer.transform(same(snapshot), any(), any())).thenAnswer(invocation -> {
            OAIRecord copy = invocation.getArgument(1);
            copy.setIdentifier("changed-by-rule");
            OAIRecordMetadata metadata = invocation.getArgument(2);
            metadata.addFieldOcurrence("dark.title", metadata.getFieldOcurrences("dc.title").get(0));
            return true;
        });

        var workingMetadata = new OAIRecordMetadata("oai:1", xml);
        assertEquals(Map.of("title", "Título"), service.transform(transformer, snapshot, record, workingMetadata, null));
        assertEquals(List.of("Título"), workingMetadata.getFieldOcurrences("dark.title"));
        assertEquals("oai:1", record.getIdentifier());
        assertTrue(new OAIRecordMetadata("oai:1", xml).getFieldOcurrences("dark.title").isEmpty());
    }

    @Test
    void onlyAPreparedNonblankTitleIsRequired() throws Exception {
        var metadata = new OAIRecordMetadata("oai:1");
        metadata.addFieldOcurrence("dc.title", "Original");
        metadata.addFieldOcurrence("dark.title", " ");
        assertThrows(IllegalArgumentException.class, () -> reader.readTransformedMetadata(metadata, null));
        metadata.addFieldOcurrence("dark.title", "Título preparado");
        assertEquals(Map.of("title", "Título preparado"), reader.readTransformedMetadata(metadata, null));
    }

    @Test
    void readsPrioritizedFieldsAndKeepsAdditionalBibliographicMetadata() throws Exception {
        var metadata = new OAIRecordMetadata("oai:1");
        metadata.addFieldOcurrence("dark.title", "Atlas");
        metadata.addFieldOcurrence("dark.author", " Ana |||0000");
        metadata.addFieldOcurrence("dark.author", "Ana");
        metadata.addFieldOcurrence("dark.author", "Instituto");
        metadata.addFieldOcurrence("dark.year.issued", "2018");
        metadata.addFieldOcurrence("dark.year.issued", "2018-07-14T15:15:42.1234+0300");
        metadata.addFieldOcurrence("dark.year.created", "2017");
        String thesis = "http://purl.org/coar/resource_type/c_7a1f";
        metadata.addFieldOcurrence("dark.type.declared", thesis);
        metadata.addFieldOcurrence("dark.type.declared", "http://purl.org/coar/resource_type/c_6501");
        metadata.addFieldOcurrence("dark.type.mapped", thesis.replace("http:", "https:"));
        metadata.addFieldOcurrence("dark.rights", "licencia");
        metadata.addFieldOcurrence("dc.language.iso", "spa");
        metadata.addFieldOcurrence("dc.subject", "Geografía");
        metadata.addFieldOcurrence("dc.identifier.uri", "https://example.org/atlas");
        metadata.addFieldOcurrence("dc.identifier.doi", "https://doi.org/10.1234/demo");

        var output = reader.readTransformedMetadata(metadata, "https://example.org/atlas");
        assertEquals(List.of("Ana", "Instituto"), output.get("authors"));
        assertEquals(2018, output.get("year"));
        assertEquals(thesis, output.get("resource_type"));
        assertEquals("licencia", output.get("rights"));
        assertEquals("es", output.get("language"));
        assertEquals(List.of("Geografía"), output.get("subjects"));
        assertEquals(List.of(Map.of("schema", "doi", "value", "10.1234/demo")), output.get("alternate_identifiers"));
        assertFalse(output.containsKey("alternate_urls"));
    }

    @Test
    void ambiguousYearsAndTypesDoNotBlockOrInventValues() throws Exception {
        var metadata = new OAIRecordMetadata("oai:1");
        metadata.addFieldOcurrence("dark.title", "Atlas");
        metadata.addFieldOcurrence("dark.year.generic", "2014");
        metadata.addFieldOcurrence("dark.year.generic", "2019-03-18T19:40:25Z");
        metadata.addFieldOcurrence("dark.type.declared", "http://purl.org/coar/resource_type/c_7a1f");
        metadata.addFieldOcurrence("dark.type.declared", "http://purl.org/coar/resource_type/c_6501");
        assertEquals(Map.of("title", "Atlas"), reader.readTransformedMetadata(metadata, null));

        metadata.addFieldOcurrence("dark.type.literal", "Trabajo final");
        assertEquals("Trabajo final", reader.readTransformedMetadata(metadata, null).get("resource_type"));
    }

    @Test
    void distinguishesMissingProfilesFromAnUnconfiguredSelection() {
        var profiles = mock(TransformerRepository.class);
        var service = new DarkL1TransformerService(profiles, mock(ValidationService.class), reader);
        assertNull(service.load(null));
        verifyNoInteractions(profiles);
        assertThrows(IllegalStateException.class, () -> service.load(40L));
    }
}
