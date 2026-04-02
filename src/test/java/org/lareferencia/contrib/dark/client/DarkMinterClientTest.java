package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lareferencia.contrib.dark.services.DarkProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DarkMinterClient tests")
class DarkMinterClientTest {

    @Test
    @DisplayName("Reserve batch uses authority header and parses results")
    void reserveBatchParsesResults() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(200, """
                {"results":[{"ark":"ark:/12345/abc","state":"R","client_item_id":"oai:1"}]}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        DarkMinterClient client = new DarkMinterClient(httpClient, new ObjectMapper(), properties());
        ReserveBatchResponse batchResponse = client.reserveBatch("authority-1", "12345", List.of("oai:1"));

        assertEquals(1, batchResponse.getResults().size());
        assertEquals("ark:/12345/abc", batchResponse.getResults().get(0).getArk());
        assertEquals(DarkRemoteState.RESERVED, batchResponse.getResults().get(0).getState());
    }

    @Test
    @DisplayName("Get ARK parses remote state")
    void getArkParsesState() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(200, """
                {"ark":"ark:/12345/abc","state":"P","target":"https://example.org/resource"}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        DarkMinterClient client = new DarkMinterClient(httpClient, new ObjectMapper(), properties());
        ARKResponse ark = client.getArk("ark:/12345/abc");

        assertEquals(DarkRemoteState.PUBLISHED, ark.getState());
        assertEquals("https://example.org/resource", ark.getTarget());
    }

    @Test
    @DisplayName("Raise client exception on HTTP errors")
    void raisesClientExceptionOnHttpError() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(404, "{\"detail\":\"ARK not found\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        DarkMinterClient client = new DarkMinterClient(httpClient, new ObjectMapper(), properties());

        DarkMinterClientException error = assertThrows(DarkMinterClientException.class,
                () -> client.getArk("ark:/12345/missing"));
        assertEquals(404, error.getStatusCode());
        assertEquals("GET", error.getMethod());
        assertEquals(URI.create("http://localhost:8001/api/v1/arks/ark:/12345/missing"), error.getUri());
        assertEquals("{\"detail\":\"ARK not found\"}", error.getResponseBody());
        assertEquals("dARK minter error 404 on GET http://localhost:8001/api/v1/arks/ark:/12345/missing: ARK not found", error.getMessage());
    }

    private DarkProperties properties() {
        DarkProperties properties = new DarkProperties();
        properties.setAuthorityId("authority-1");
        properties.getMinter().setBaseUrl("http://localhost:8001");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(response.uri()).thenReturn(URI.create("http://localhost:8001/api/v1/arks"));
        when(response.headers()).thenReturn(HttpHeaders.of(java.util.Map.of(), (a, b) -> true));
        return response;
    }
}
