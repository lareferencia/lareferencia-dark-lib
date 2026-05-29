package org.lareferencia.contrib.dark.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lareferencia.contrib.dark.client.ARKResponse;
import org.lareferencia.contrib.dark.client.DarkMinterClient;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.lareferencia.contrib.dark.client.DarkRemoteState;
import org.lareferencia.contrib.dark.client.ReserveBatchResponse;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecordId;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.DarkLevel1MetadataService;
import org.lareferencia.contrib.dark.services.DarkNetworkSettingsResolver;
import org.lareferencia.contrib.dark.services.DarkOriginalMetadataTransformerService;
import org.lareferencia.contrib.dark.services.DarkProperties;
import org.lareferencia.contrib.dark.services.UrlExtractionService;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.metadata.IMetadataStore;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.CatalogDatabaseManager;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("DarkStageWorker tests")
class DarkStageWorkerTest {

    @Mock
    private IMetadataStore metadataStore;

    @Mock
    private UrlExtractionService urlExtractionService;

    @Mock
    private DarkLevel1MetadataService level1MetadataService;

    @Mock
    private DarkTrackingRepository darkTrackingRepository;

    @Mock
    private DarkMinterClient darkMinterClient;

    @Mock
    private DarkNetworkSettingsResolver darkNetworkSettingsResolver;

    @Mock
    private CatalogDatabaseManager catalogDatabaseManager;

    @Mock
    private DarkOriginalMetadataTransformerService darkOriginalMetadataTransformerService;

    @InjectMocks
    private DarkStageWorker worker;

    private final DarkProperties properties = new DarkProperties();

    @BeforeEach
    void setUp() {
        properties.setAuthorityId("authority-1");
        properties.setMetadataSchema("dublin_core");
        properties.setReserveBatchSize(100);
        ReflectionTestUtils.setField(worker, "darkProperties", properties);
        ReflectionTestUtils.setField(worker, "snapshotMetadata", mockSnapshot());
        ReflectionTestUtils.setField(worker, "currentArkNaan", "12345");
        ReflectionTestUtils.setField(worker, "currentSourceMetadataSchema", "xoai");
        ReflectionTestUtils.setField(worker, "currentTargetMetadataSchema", "dublin_core");
        ReflectionTestUtils.setField(worker, "runningContext", new NetworkRunningContext(mockNetwork()));
    }

    @Test
    @DisplayName("Reserve and stage a new record to draft")
    void reservesAndStagesNewRecord() throws Exception {
        OAIRecord record = OAIRecord.create("oai:test:1", null, "hash-1", false);
        when(metadataStore.getMetadata(any(), eq("hash-1"))).thenReturn("<metadata/>");
        when(darkOriginalMetadataTransformerService.transformForMinter(eq("oai:test:1"), isNull(), eq("<metadata/>"), eq("xoai"), eq("dublin_core")))
                .thenReturn("<metadata/>");
        when(urlExtractionService.extractBestUrl(any())).thenReturn("https://example.org/resource/1");
        when(level1MetadataService.buildMinimalMetadata(eq("oai:test:1"), any(), eq("https://example.org/resource/1")))
                .thenReturn(Map.of("title", "Demo", "authors", List.of("Ada"), "year", 2026));
        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:1"))).thenReturn(Optional.empty());

        ReserveBatchResponse reserveResponse = new ReserveBatchResponse();
        ARKResponse reserveItem = new ARKResponse();
        reserveItem.setArk("ark:/12345/abc");
        reserveItem.setState(DarkRemoteState.RESERVED);
        reserveItem.setClientItemId("oai:test:1");
        reserveResponse.setResults(List.of(reserveItem));
        when(darkMinterClient.reserveBatch("authority-1", "12345", List.of("oai:test:1")))
                .thenReturn(reserveResponse);

        ARKResponse stageResponse = new ARKResponse();
        stageResponse.setArk("ark:/12345/abc");
        stageResponse.setState(DarkRemoteState.DRAFT);
        when(darkMinterClient.stageArk(eq("ark:/12345/abc"), any())).thenReturn(stageResponse);

        worker.prePage();
        worker.processItem(record);
        worker.postPage();

        ArgumentCaptor<DarkTrackingRecord> captor = ArgumentCaptor.forClass(DarkTrackingRecord.class);
        verify(darkTrackingRepository, times(2)).save(captor.capture());
        DarkTrackingRecord reserved = captor.getAllValues().get(0);
        assertEquals("12345", reserved.getArkNaan());
        assertEquals("oai:test:1", reserved.getOaiId());
        assertEquals("ark:/12345/abc", reserved.getArk());
        assertEquals(DarkTrackingState.RESERVED, reserved.getState());
        assertEquals(null, reserved.getSourceMetadataHash());
        assertEquals(null, reserved.getTargetUrl());

        DarkTrackingRecord saved = captor.getAllValues().get(1);
        assertEquals("12345", saved.getArkNaan());
        assertEquals("oai:test:1", saved.getOaiId());
        assertEquals("ark:/12345/abc", saved.getArk());
        assertEquals("hash-1", saved.getSourceMetadataHash());
        assertEquals("https://example.org/resource/1", saved.getTargetUrl());
        assertEquals(DarkTrackingState.DRAFT, saved.getState());
    }

    @Test
    @DisplayName("Skip remote calls when tracking payload is unchanged")
    void skipsUnchangedTrackedRecord() throws Exception {
        OAIRecord record = OAIRecord.create("oai:test:2", null, "hash-2", false);
        DarkTrackingRecord trackingRecord = new DarkTrackingRecord();
        trackingRecord.setOaiId("oai:test:2");
        trackingRecord.setArkNaan("12345");
        trackingRecord.setArk("ark:/12345/existing");
        trackingRecord.setSourceMetadataHash("hash-2");
        trackingRecord.setTargetUrl("https://example.org/resource/2");
        trackingRecord.setState(DarkTrackingState.DRAFT);

        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:2"))).thenReturn(Optional.of(trackingRecord));

        worker.prePage();
        worker.processItem(record);
        worker.postPage();

        verify(metadataStore, never()).getMetadata(any(), any());
        verify(darkOriginalMetadataTransformerService, never()).transformForMinter(any(), any(), any(), any(), any());
        verify(urlExtractionService, never()).extractBestUrl(any());
        verify(level1MetadataService, never()).buildMinimalMetadata(any(), any(), any());
        verify(darkMinterClient, never()).reserveBatch(any(), any(), any());
        verify(darkMinterClient, never()).stageArk(any(), any());
        verify(darkTrackingRepository, never()).save(any(DarkTrackingRecord.class));
    }

    @Test
    @DisplayName("Stop on systemic minter reservation failure")
    void stopsOnSystemicReservationFailure() throws Exception {
        OAIRecord record = OAIRecord.create("oai:test:3", null, "hash-3", false);
        when(metadataStore.getMetadata(any(), eq("hash-3"))).thenReturn("<metadata/>");
        when(darkOriginalMetadataTransformerService.transformForMinter(eq("oai:test:3"), isNull(), eq("<metadata/>"), eq("xoai"), eq("dublin_core")))
                .thenReturn("<metadata/>");
        when(urlExtractionService.extractBestUrl(any())).thenReturn("https://example.org/resource/3");
        when(level1MetadataService.buildMinimalMetadata(eq("oai:test:3"), any(), eq("https://example.org/resource/3")))
                .thenReturn(Map.of("title", "Demo", "authors", List.of("Ada"), "year", 2026));
        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:3"))).thenReturn(Optional.empty());

        ReserveBatchResponse reserveResponse = new ReserveBatchResponse();
        ReserveBatchResponse.BatchError batchError = new ReserveBatchResponse.BatchError();
        batchError.setClientItemId("oai:test:3");
        batchError.setError("(psycopg2.errors.InFailedSqlTransaction) current transaction is aborted, commands ignored until end of transaction block");
        reserveResponse.setErrors(List.of(batchError));
        when(darkMinterClient.reserveBatch("authority-1", "12345", List.of("oai:test:3")))
                .thenReturn(reserveResponse);

        worker.prePage();
        worker.processItem(record);

        assertThrows(DarkMinterClientException.class, () -> worker.postPage());
        verify(darkMinterClient, never()).stageArk(any(), any());
        verify(darkTrackingRepository, never()).save(any(DarkTrackingRecord.class));
    }

    @Test
    @DisplayName("Stop on systemic stage failure and keep reserved ARK retryable")
    void stopsOnSystemicStageFailureAfterReservation() throws Exception {
        OAIRecord record = OAIRecord.create("oai:test:4", null, "hash-4", false);
        when(metadataStore.getMetadata(any(), eq("hash-4"))).thenReturn("<metadata/>");
        when(darkOriginalMetadataTransformerService.transformForMinter(eq("oai:test:4"), isNull(), eq("<metadata/>"), eq("xoai"), eq("dublin_core")))
                .thenReturn("<metadata/>");
        when(urlExtractionService.extractBestUrl(any())).thenReturn("https://example.org/resource/4");
        when(level1MetadataService.buildMinimalMetadata(eq("oai:test:4"), any(), eq("https://example.org/resource/4")))
                .thenReturn(Map.of("title", "Demo", "authors", List.of("Ada"), "year", 2026));
        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:4"))).thenReturn(Optional.empty());

        ReserveBatchResponse reserveResponse = new ReserveBatchResponse();
        ARKResponse reserveItem = new ARKResponse();
        reserveItem.setArk("ark:/12345/retry");
        reserveItem.setState(DarkRemoteState.RESERVED);
        reserveItem.setClientItemId("oai:test:4");
        reserveResponse.setResults(List.of(reserveItem));
        when(darkMinterClient.reserveBatch("authority-1", "12345", List.of("oai:test:4")))
                .thenReturn(reserveResponse);
        when(darkMinterClient.stageArk(eq("ark:/12345/retry"), any()))
                .thenThrow(new DarkMinterClientException(500, "Internal server error"));

        worker.prePage();
        worker.processItem(record);
        worker.postPage();

        ArgumentCaptor<DarkTrackingRecord> captor = ArgumentCaptor.forClass(DarkTrackingRecord.class);
        verify(darkTrackingRepository, times(2)).save(captor.capture());
        DarkTrackingRecord reserved = captor.getAllValues().get(0);
        assertEquals("oai:test:4", reserved.getOaiId());
        assertEquals("ark:/12345/retry", reserved.getArk());
        assertEquals(DarkTrackingState.RESERVED, reserved.getState());
        assertEquals(null, reserved.getSourceMetadataHash());
        assertEquals(null, reserved.getTargetUrl());

        DarkTrackingRecord saved = captor.getAllValues().get(1);
        assertEquals("oai:test:4", saved.getOaiId());
        assertEquals("ark:/12345/retry", saved.getArk());
        assertEquals(DarkTrackingState.RESERVED, saved.getState());
        assertEquals(null, saved.getSourceMetadataHash());
        assertEquals(null, saved.getTargetUrl());
        assertEquals("dARK minter error 500: Internal server error", saved.getLastError());
    }

    @Test
    @DisplayName("Keep confirmed hash when retryable update fails")
    void keepsConfirmedHashWhenRetryableUpdateFails() throws Exception {
        OAIRecord record = OAIRecord.create("oai:test:5", null, "hash-5-new", false);
        DarkTrackingRecord trackingRecord = new DarkTrackingRecord();
        trackingRecord.setOaiId("oai:test:5");
        trackingRecord.setArkNaan("12345");
        trackingRecord.setArk("ark:/12345/update");
        trackingRecord.setSourceMetadataHash("hash-5-old");
        trackingRecord.setTargetUrl("https://example.org/resource/5/old");
        trackingRecord.setState(DarkTrackingState.PUBLISHED);

        when(metadataStore.getMetadata(any(), eq("hash-5-new"))).thenReturn("<metadata/>");
        when(darkOriginalMetadataTransformerService.transformForMinter(eq("oai:test:5"), isNull(), eq("<metadata/>"), eq("xoai"), eq("dublin_core")))
                .thenReturn("<metadata/>");
        when(urlExtractionService.extractBestUrl(any())).thenReturn("https://example.org/resource/5/new");
        when(level1MetadataService.buildMinimalMetadata(eq("oai:test:5"), any(), eq("https://example.org/resource/5/new")))
                .thenReturn(Map.of("title", "Demo", "authors", List.of("Ada"), "year", 2026));
        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:5"))).thenReturn(Optional.of(trackingRecord));
        when(darkMinterClient.stageArk(eq("ark:/12345/update"), any()))
                .thenThrow(new DarkMinterClientException(500, "Internal server error"));

        worker.prePage();
        worker.processItem(record);
        worker.postPage();

        ArgumentCaptor<DarkTrackingRecord> captor = ArgumentCaptor.forClass(DarkTrackingRecord.class);
        verify(darkTrackingRepository).save(captor.capture());
        DarkTrackingRecord saved = captor.getValue();
        assertEquals(DarkTrackingState.UPDATE, saved.getState());
        assertEquals("hash-5-old", saved.getSourceMetadataHash());
        assertEquals("https://example.org/resource/5/old", saved.getTargetUrl());
        assertEquals("dARK minter error 500: Internal server error", saved.getLastError());
    }

    @Test
    @DisplayName("Update confirmed hash only after successful stage")
    void updatesConfirmedHashOnlyAfterSuccessfulStage() throws Exception {
        OAIRecord record = OAIRecord.create("oai:test:6", null, "hash-6-new", false);
        DarkTrackingRecord trackingRecord = new DarkTrackingRecord();
        trackingRecord.setOaiId("oai:test:6");
        trackingRecord.setArkNaan("12345");
        trackingRecord.setArk("ark:/12345/update-success");
        trackingRecord.setSourceMetadataHash("hash-6-old");
        trackingRecord.setTargetUrl("https://example.org/resource/6/old");
        trackingRecord.setState(DarkTrackingState.PUBLISHED);

        when(metadataStore.getMetadata(any(), eq("hash-6-new"))).thenReturn("<metadata/>");
        when(darkOriginalMetadataTransformerService.transformForMinter(eq("oai:test:6"), isNull(), eq("<metadata/>"), eq("xoai"), eq("dublin_core")))
                .thenReturn("<metadata/>");
        when(urlExtractionService.extractBestUrl(any())).thenReturn("https://example.org/resource/6/new");
        when(level1MetadataService.buildMinimalMetadata(eq("oai:test:6"), any(), eq("https://example.org/resource/6/new")))
                .thenReturn(Map.of("title", "Demo", "authors", List.of("Ada"), "year", 2026));
        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:6"))).thenReturn(Optional.of(trackingRecord));

        ARKResponse stageResponse = new ARKResponse();
        stageResponse.setArk("ark:/12345/update-success");
        stageResponse.setState(DarkRemoteState.DRAFT);
        when(darkMinterClient.stageArk(eq("ark:/12345/update-success"), any())).thenReturn(stageResponse);

        worker.prePage();
        worker.processItem(record);
        worker.postPage();

        ArgumentCaptor<DarkTrackingRecord> captor = ArgumentCaptor.forClass(DarkTrackingRecord.class);
        verify(darkTrackingRepository).save(captor.capture());
        DarkTrackingRecord saved = captor.getValue();
        assertEquals(DarkTrackingState.DRAFT, saved.getState());
        assertEquals("hash-6-new", saved.getSourceMetadataHash());
        assertEquals("https://example.org/resource/6/new", saved.getTargetUrl());
        assertEquals(null, saved.getLastError());
    }

    @Test
    @DisplayName("Stage update state even when confirmed hash matches snapshot")
    void stagesUpdateStateEvenWhenHashMatches() throws Exception {
        OAIRecord record = OAIRecord.create("oai:test:7", null, "hash-7", false);
        DarkTrackingRecord trackingRecord = new DarkTrackingRecord();
        trackingRecord.setOaiId("oai:test:7");
        trackingRecord.setArkNaan("12345");
        trackingRecord.setArk("ark:/12345/update-state");
        trackingRecord.setSourceMetadataHash("hash-7");
        trackingRecord.setTargetUrl("https://example.org/resource/7");
        trackingRecord.setState(DarkTrackingState.UPDATE);

        when(metadataStore.getMetadata(any(), eq("hash-7"))).thenReturn("<metadata/>");
        when(darkOriginalMetadataTransformerService.transformForMinter(eq("oai:test:7"), isNull(), eq("<metadata/>"), eq("xoai"), eq("dublin_core")))
                .thenReturn("<metadata/>");
        when(urlExtractionService.extractBestUrl(any())).thenReturn("https://example.org/resource/7");
        when(level1MetadataService.buildMinimalMetadata(eq("oai:test:7"), any(), eq("https://example.org/resource/7")))
                .thenReturn(Map.of("title", "Demo", "authors", List.of("Ada"), "year", 2026));
        when(darkTrackingRepository.findById(DarkTrackingRecordId.of("12345", "oai:test:7"))).thenReturn(Optional.of(trackingRecord));

        ARKResponse stageResponse = new ARKResponse();
        stageResponse.setArk("ark:/12345/update-state");
        stageResponse.setState(DarkRemoteState.DRAFT);
        when(darkMinterClient.stageArk(eq("ark:/12345/update-state"), any())).thenReturn(stageResponse);

        worker.prePage();
        worker.processItem(record);
        worker.postPage();

        verify(metadataStore).getMetadata(any(), eq("hash-7"));
        verify(darkMinterClient).stageArk(eq("ark:/12345/update-state"), any());
        verify(darkTrackingRepository).save(trackingRecord);
        assertEquals(DarkTrackingState.DRAFT, trackingRecord.getState());
    }

    private SnapshotMetadata mockSnapshot() {
        return mock(SnapshotMetadata.class);
    }

    private Network mockNetwork() {
        Network network = new Network();
        network.setAcronym("TEST");
        return network;
    }
}
