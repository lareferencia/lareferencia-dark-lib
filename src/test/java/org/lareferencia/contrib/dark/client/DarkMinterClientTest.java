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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @DisplayName("Reserve batch normalizes authority and NAAN")
    void reserveBatchNormalizesAuthorityAndNaan() {
        ReserveBatchRequest request = ReserveBatchRequest.fromClientItemIds(
                " resolver-e2e-1779799552\t",
                " 12345 ",
                List.of("oai:1"));

        assertEquals("resolver-e2e-1779799552", request.getAuthorityId());
        assertEquals("12345", request.getNaan());
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
        assertEquals(DarkMinterClientException.UNKNOWN_ERROR_CODE, error.getErrorCode());
        assertFalse(error.isRetryable());
        assertEquals("{\"detail\":\"ARK not found\"}", error.getResponseBody());
        assertEquals("dARK minter error 404 on GET http://localhost:8001/api/v1/arks/ark:/12345/missing: ARK not found", error.getMessage());
    }

    @Test
    @DisplayName("HTTP errors preserve dARK error headers")
    void preservesDarkErrorHeaders() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(
                503,
                "{\"detail\":\"Authorization check unavailable\"}",
                Map.of(
                        "X-DARK-Error-Code", List.of("AUTHORIZATION_CHECK_UNAVAILABLE"),
                        "X-DARK-Retryable", List.of("true")));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        DarkMinterClient client = new DarkMinterClient(httpClient, new ObjectMapper(), properties());

        DarkMinterClientException error = assertThrows(DarkMinterClientException.class,
                () -> client.getArk("ark:/12345/pending"));
        assertEquals(503, error.getStatusCode());
        assertEquals("AUTHORIZATION_CHECK_UNAVAILABLE", error.getErrorCode());
        assertTrue(error.isRetryable());
        assertTrue(error.isSystemic());
        assertEquals("Authorization check unavailable", error.getMessage().substring(error.getMessage().lastIndexOf(": ") + 2));
    }

    @Test
    @DisplayName("Retryable falls back to HTTP status when header is absent")
    void retryableFallsBackToStatusWhenHeaderIsAbsent() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(500, "{\"detail\":\"Internal server error\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        DarkMinterClient client = new DarkMinterClient(httpClient, new ObjectMapper(), properties());

        DarkMinterClientException error = assertThrows(DarkMinterClientException.class,
                () -> client.getArk("ark:/12345/pending"));
        assertEquals(DarkMinterClientException.UNKNOWN_ERROR_CODE, error.getErrorCode());
        assertTrue(error.isRetryable());
    }

    @Test
    @DisplayName("Authorization failure can be non retryable")
    void authorizationFailureCanBeNonRetryable() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockResponse(
                403,
                "{\"detail\":\"Authority not authorized for NAAN\"}",
                Map.of(
                        "X-DARK-Error-Code", List.of("AUTHORIZATION_FAILED"),
                        "X-DARK-Retryable", List.of("false")));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        DarkMinterClient client = new DarkMinterClient(httpClient, new ObjectMapper(), properties());

        DarkMinterClientException error = assertThrows(DarkMinterClientException.class,
                () -> client.getArk("ark:/12345/pending"));
        assertEquals("AUTHORIZATION_FAILED", error.getErrorCode());
        assertFalse(error.isRetryable());
        assertTrue(error.isSystemic());
    }

    private DarkProperties properties() {
        DarkProperties properties = new DarkProperties();
        properties.setAuthorityId(" authority-1\t");
        properties.setAuthHeaderName(" X-Authority-Id ");
        properties.getMinter().setBaseUrl(" http://localhost:8001 ");
        assertEquals("authority-1", properties.getAuthorityId());
        assertEquals("X-Authority-Id", properties.getAuthHeaderName());
        assertEquals("http://localhost:8001", properties.getMinter().getBaseUrl());
        return properties;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body) {
        return mockResponse(statusCode, body, Map.of());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body, Map<String, List<String>> headers) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(response.uri()).thenReturn(URI.create("http://localhost:8001/api/v1/arks"));
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (a, b) -> true));
        return response;
    }
}
