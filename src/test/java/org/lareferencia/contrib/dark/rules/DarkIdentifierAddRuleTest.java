package org.lareferencia.contrib.dark.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecordId;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.DarkNetworkSettingsResolver;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.domain.IOAIRecord;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DarkIdentifierAddRule tests")
class DarkIdentifierAddRuleTest {

    @Mock
    private DarkTrackingRepository darkTrackingRepository;

    @Mock
    private IOAIRecord record;

    @Mock
    private DarkNetworkSettingsResolver darkNetworkSettingsResolver;

    @InjectMocks
    private DarkIdentifierAddRule rule;

    @Test
    @DisplayName("Inject ARK only when record is published")
    void injectsPublishedArk() throws Exception {
        DarkTrackingRecord trackingRecord = new DarkTrackingRecord();
        trackingRecord.setOaiId("oai:test:1");
        trackingRecord.setArkNaan("12345");
        trackingRecord.setArk("ark:/12345/abc");
        trackingRecord.setState(DarkTrackingState.PUBLISHED);

        when(record.getIdentifier()).thenReturn("oai:test:1");
        when(darkNetworkSettingsResolver.resolveArkNaan(anyMap(), eq("TEST(id:1)"))).thenReturn("12345");
        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:1"))).thenReturn(Optional.of(trackingRecord));

        OAIRecordMetadata metadata = new OAIRecordMetadata("oai:test:1");
        boolean transformed = rule.transform(snapshotMetadata(), record, metadata);

        assertTrue(transformed);
        verify(darkTrackingRepository).findById(DarkTrackingRecordId.of("12345", "oai:test:1"));
    }

    @Test
    @DisplayName("Do not inject ARK when record is not published")
    void skipsNonPublishedArk() throws Exception {
        DarkTrackingRecord trackingRecord = new DarkTrackingRecord();
        trackingRecord.setOaiId("oai:test:2");
        trackingRecord.setArkNaan("12345");
        trackingRecord.setArk("ark:/12345/def");
        trackingRecord.setState(DarkTrackingState.DRAFT);

        when(record.getIdentifier()).thenReturn("oai:test:2");
        when(darkNetworkSettingsResolver.resolveArkNaan(anyMap(), eq("TEST(id:1)"))).thenReturn("12345");
        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:2"))).thenReturn(Optional.of(trackingRecord));

        OAIRecordMetadata metadata = new OAIRecordMetadata("oai:test:2");
        boolean transformed = rule.transform(snapshotMetadata(), record, metadata);

        assertFalse(transformed);
    }

    @Test
    @DisplayName("Do not inject duplicated ARK when metadata already contains it")
    void skipsDuplicatedPublishedArk() throws Exception {
        DarkTrackingRecord trackingRecord = new DarkTrackingRecord();
        trackingRecord.setOaiId("oai:test:3");
        trackingRecord.setArkNaan("12345");
        trackingRecord.setArk("ark:/12345/ghi");
        trackingRecord.setState(DarkTrackingState.PUBLISHED);

        when(record.getIdentifier()).thenReturn("oai:test:3");
        when(darkNetworkSettingsResolver.resolveArkNaan(anyMap(), eq("TEST(id:1)"))).thenReturn("12345");
        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:3"))).thenReturn(Optional.of(trackingRecord));

        OAIRecordMetadata metadata = new OAIRecordMetadata("oai:test:3");
        metadata.addFieldOcurrence(DarkIdentifierAddRule.DC_IDENTIFIER_DARK, "ark:/12345/ghi");

        boolean transformed = rule.transform(snapshotMetadata(), record, metadata);

        assertFalse(transformed);
        assertEquals(1, metadata.getFieldOcurrences(DarkIdentifierAddRule.DC_IDENTIFIER_DARK).size());
        assertEquals("ark:/12345/ghi", metadata.getFieldOcurrences(DarkIdentifierAddRule.DC_IDENTIFIER_DARK).get(0));
    }

    private SnapshotMetadata snapshotMetadata() {
        Network network = new Network();
        network.setAcronym("TEST");
        org.springframework.test.util.ReflectionTestUtils.setField(network, "id", 1L);
        network.setAttributes(java.util.Map.of("ark_naan", "12345"));

        SnapshotMetadata snapshotMetadata = new SnapshotMetadata();
        snapshotMetadata.setNetwork(network);
        return snapshotMetadata;
    }
}
