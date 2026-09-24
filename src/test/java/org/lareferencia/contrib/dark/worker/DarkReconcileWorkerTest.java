package org.lareferencia.contrib.dark.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lareferencia.contrib.dark.client.ARKResponse;
import org.lareferencia.contrib.dark.client.ArkStatusBatchError;
import org.lareferencia.contrib.dark.client.ArkStatusBatchResponse;
import org.lareferencia.contrib.dark.client.ArkStatusBatchResult;
import org.lareferencia.contrib.dark.client.DarkMinterClient;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.lareferencia.contrib.dark.client.DarkRemoteState;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.DarkNetworkSettingsResolver;
import org.lareferencia.contrib.dark.services.DarkProperties;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DarkReconcileWorker tests")
class DarkReconcileWorkerTest {

    @Mock
    private DarkTrackingRepository darkTrackingRepository;

    @Mock
    private DarkMinterClient darkMinterClient;

    @Mock
    private DarkProperties darkProperties;

    @Mock
    private DarkNetworkSettingsResolver darkNetworkSettingsResolver;

    @InjectMocks
    private DarkReconcileWorker worker;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "currentArkNaan", "12345");
        Network network = new Network();
        network.setAcronym("TEST");
        ReflectionTestUtils.setField(worker, "runningContext", new NetworkRunningContext(network));
    }

    @Test
    @DisplayName("Include reserved records in reconciliation states")
    void includesReservedRecordsInReconciliationStates() {
        DarkProperties properties = new DarkProperties();
        Network network = new Network();
        network.setAcronym("TEST");
        ReflectionTestUtils.setField(worker, "darkProperties", properties);
        ReflectionTestUtils.setField(worker, "runningContext", new NetworkRunningContext(network));
        when(darkNetworkSettingsResolver.resolveArkNaan(network)).thenReturn("12345");

        worker.preRun();

        ArgumentCaptor<Collection<DarkTrackingState>> statesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(darkTrackingRepository).countByIdArkNaanAndArkIsNotNullAndStateIn(eq("12345"), statesCaptor.capture());
        assertTrue(statesCaptor.getValue().contains(DarkTrackingState.RESERVED));
    }

    @Test
    @DisplayName("Promote local record to published when remote state is published")
    void promotesToPublished() {
        DarkTrackingRecord record = new DarkTrackingRecord();
        record.setOaiId("oai:test:1");
        record.setArkNaan("12345");
        record.setArk("ark:12345/abc");
        record.setState(DarkTrackingState.DRAFT);

        ARKResponse response = new ARKResponse();
        response.setArk("ark:12345/abc");
        response.setState(DarkRemoteState.PUBLISHED);

        when(darkMinterClient.getArkStatuses(List.of("ark:12345/abc"))).thenReturn(batchStatus(response));

        reconcile(record);

        assertEquals(DarkTrackingState.PUBLISHED, record.getState());
        verify(darkTrackingRepository).save(record);
    }

    @Test
    @DisplayName("Mark record as error when reconcile call fails")
    void marksAsErrorOnFailure() {
        DarkTrackingRecord record = new DarkTrackingRecord();
        record.setOaiId("oai:test:2");
        record.setArkNaan("12345");
        record.setArk("ark:12345/def");
        record.setState(DarkTrackingState.UPDATE);

        when(darkMinterClient.getArkStatuses(List.of("ark:12345/def")))
                .thenReturn(batchError("ark:12345/def", "not_found", "ARK not found", false));

        reconcile(record);

        assertEquals(DarkTrackingState.ERROR, record.getState());
        assertTrue(record.getLastError().contains("\"category\":\"REMOTE_PERMANENT\""));
        assertTrue(record.getLastError().contains("\"phase\":\"READ_REMOTE_STATE\""));
        verify(darkTrackingRepository).save(record);
    }

    @Test
    @DisplayName("Stop without marking record error when minter failure is systemic")
    void stopsOnSystemicFailure() {
        DarkTrackingRecord record = new DarkTrackingRecord();
        record.setOaiId("oai:test:3");
        record.setArkNaan("12345");
        record.setArk("ark:12345/systemic");
        record.setState(DarkTrackingState.DRAFT);

        when(darkMinterClient.getArkStatuses(List.of("ark:12345/systemic")))
                .thenReturn(batchError("ark:12345/systemic", "blockchain_error", "RPC unavailable", true));

        worker.prePage();
        worker.processItem(record);
        assertThrows(DarkMinterClientException.class, worker::postPage);

        assertEquals(DarkTrackingState.DRAFT, record.getState());
        verify(darkTrackingRepository, never()).save(record);
    }

    @Test
    @DisplayName("Reconcile reserved record without confirming payload hash")
    void reconcilesReservedRecordWithoutConfirmingPayloadHash() {
        DarkTrackingRecord record = new DarkTrackingRecord();
        record.setOaiId("oai:test:4");
        record.setArkNaan("12345");
        record.setArk("ark:12345/reserved");
        record.setState(DarkTrackingState.RESERVED);

        ARKResponse response = new ARKResponse();
        response.setArk("ark:12345/reserved");
        response.setState(DarkRemoteState.DRAFT);

        when(darkMinterClient.getArkStatuses(List.of("ark:12345/reserved"))).thenReturn(batchStatus(response));

        reconcile(record);

        assertEquals(DarkTrackingState.DRAFT, record.getState());
        assertEquals(null, record.getSourceMetadataHash());
        assertEquals(null, record.getTargetUrl());
        verify(darkTrackingRepository).save(record);
    }

    @Test
    @DisplayName("Use one status batch for all records in the page")
    void reconcilesPageWithOneBatchRequest() {
        DarkTrackingRecord first = record("oai:test:5", "ark:12345/first");
        DarkTrackingRecord second = record("oai:test:6", "ark:12345/second");
        ARKResponse published = new ARKResponse();
        published.setArk(first.getArk());
        published.setState(DarkRemoteState.PUBLISHED);
        ARKResponse draft = new ARKResponse();
        draft.setArk(second.getArk());
        draft.setState(DarkRemoteState.DRAFT);
        ArkStatusBatchResponse response = new ArkStatusBatchResponse();
        response.setVersion("v1");
        response.setResults(List.of(status(first.getArk(), published), status(second.getArk(), draft)));
        when(darkMinterClient.getArkStatuses(List.of(first.getArk(), second.getArk()))).thenReturn(response);

        worker.prePage();
        worker.processItem(first);
        worker.processItem(second);
        worker.postPage();

        verify(darkMinterClient).getArkStatuses(List.of(first.getArk(), second.getArk()));
        verify(darkMinterClient, never()).getArk(org.mockito.ArgumentMatchers.any());
        assertEquals(DarkTrackingState.PUBLISHED, first.getState());
        assertEquals(DarkTrackingState.DRAFT, second.getState());
    }

    private void reconcile(DarkTrackingRecord record) {
        worker.prePage();
        worker.processItem(record);
        worker.postPage();
    }

    private DarkTrackingRecord record(String oaiId, String ark) {
        DarkTrackingRecord record = new DarkTrackingRecord();
        record.setOaiId(oaiId);
        record.setArkNaan("12345");
        record.setArk(ark);
        record.setState(DarkTrackingState.DRAFT);
        return record;
    }

    private ArkStatusBatchResponse batchStatus(ARKResponse status) {
        ArkStatusBatchResponse response = new ArkStatusBatchResponse();
        response.setVersion("v1");
        response.setResults(List.of(status(status.getArk(), status)));
        return response;
    }

    private ArkStatusBatchResponse batchError(String ark, String code, String message, boolean retryable) {
        ArkStatusBatchError error = new ArkStatusBatchError();
        error.setCode(code);
        error.setMessage(message);
        error.setRetryable(retryable);
        ArkStatusBatchResult result = new ArkStatusBatchResult();
        result.setArk(ark);
        result.setError(error);
        ArkStatusBatchResponse response = new ArkStatusBatchResponse();
        response.setVersion("v1");
        response.setResults(List.of(result));
        return response;
    }

    private ArkStatusBatchResult status(String ark, ARKResponse status) {
        ArkStatusBatchResult result = new ArkStatusBatchResult();
        result.setArk(ark);
        result.setStatus(status);
        return result;
    }
}
