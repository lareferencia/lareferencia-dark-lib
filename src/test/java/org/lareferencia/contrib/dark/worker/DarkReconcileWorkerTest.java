package org.lareferencia.contrib.dark.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lareferencia.contrib.dark.client.ARKResponse;
import org.lareferencia.contrib.dark.client.DarkMinterClient;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.lareferencia.contrib.dark.client.DarkRemoteState;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.DarkNetworkSettingsResolver;
import org.lareferencia.contrib.dark.services.DarkProperties;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }

    @Test
    @DisplayName("Promote local record to published when remote state is published")
    void promotesToPublished() {
        DarkTrackingRecord record = new DarkTrackingRecord();
        record.setOaiId("oai:test:1");
        record.setArkNaan("12345");
        record.setArk("ark:/12345/abc");
        record.setState(DarkTrackingState.DRAFT);

        ARKResponse response = new ARKResponse();
        response.setArk("ark:/12345/abc");
        response.setState(DarkRemoteState.PUBLISHED);

        when(darkMinterClient.getArk("ark:/12345/abc")).thenReturn(response);

        worker.processItem(record);

        assertEquals(DarkTrackingState.PUBLISHED, record.getState());
        verify(darkTrackingRepository).save(record);
    }

    @Test
    @DisplayName("Mark record as error when reconcile call fails")
    void marksAsErrorOnFailure() {
        DarkTrackingRecord record = new DarkTrackingRecord();
        record.setOaiId("oai:test:2");
        record.setArkNaan("12345");
        record.setArk("ark:/12345/def");
        record.setState(DarkTrackingState.UPDATE);

        when(darkMinterClient.getArk("ark:/12345/def"))
                .thenThrow(new DarkMinterClientException(404, "ARK not found"));

        worker.processItem(record);

        assertEquals(DarkTrackingState.ERROR, record.getState());
        assertEquals("dARK minter error 404: ARK not found", record.getLastError());
        verify(darkTrackingRepository).save(record);
    }
}
