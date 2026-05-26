package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.contrib.dark.services.DarkProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * HTTP client for the new dARK minter API.
 */
@Component
public class DarkMinterClient {

    private static final Logger logger = LogManager.getLogger(DarkMinterClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DarkProperties properties;

    @Autowired
    public DarkMinterClient(DarkProperties properties) {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(REQUEST_TIMEOUT)
                .build(), new ObjectMapper(), properties);
    }

    DarkMinterClient(HttpClient httpClient, ObjectMapper objectMapper, DarkProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ReserveBatchResponse reserveBatch(String authorityId, String naan, List<String> clientItemIds) {
        String normalizedAuthorityId = normalizeRequired(authorityId, "authorityId");
        String normalizedNaan = normalizeRequired(naan, "naan");
        ReserveBatchRequest request = ReserveBatchRequest.fromClientItemIds(normalizedAuthorityId, normalizedNaan, clientItemIds);
        return sendJsonRequest(
                HttpRequest.newBuilder(buildUri("/api/v1/arks/batch"))
                        .header("Content-Type", "application/json")
                        .header(authHeaderName(), normalizedAuthorityId)
                        .POST(HttpRequest.BodyPublishers.ofString(writeJson(request))),
                ReserveBatchResponse.class);
    }

    public ARKResponse stageArk(String ark, StageArkRequest request) {
        String normalizedAuthorityId = normalizeRequired(request.getAuthorityId(), "authorityId");
        request.setAuthorityId(normalizedAuthorityId);
        return sendJsonRequest(
                HttpRequest.newBuilder(buildUri("/api/v1/arks/" + ark))
                        .header("Content-Type", "application/json")
                        .header(authHeaderName(), normalizedAuthorityId)
                        .PUT(HttpRequest.BodyPublishers.ofString(writeJson(request))),
                ARKResponse.class);
    }

    public ARKResponse getArk(String ark) {
        return sendJsonRequest(
                HttpRequest.newBuilder(buildUri("/api/v1/arks/" + ark))
                        .GET(),
                ARKResponse.class);
    }

    private <T> T sendJsonRequest(HttpRequest.Builder builder, Class<T> responseType) {
        HttpRequest request = builder.timeout(REQUEST_TIMEOUT).build();
        logger.debug("Calling dARK minter [{} {}]", request.method(), request.uri());

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            logger.debug("dARK minter responded [{}] {}", response.statusCode(), body);

            if (response.statusCode() >= 400) {
                String errorMessage = extractErrorMessage(body);
                logger.warn("dARK minter rejected request [{} {}] with status {} and body {}",
                        request.method(), request.uri(), response.statusCode(), body);
                throw new DarkMinterClientException(
                        response.statusCode(),
                        request.method(),
                        request.uri(),
                        errorMessage,
                        body);
            }

            return objectMapper.readValue(body, responseType);
        } catch (IOException e) {
            throw new DarkMinterClientException(500, request.method(), request.uri(), "I/O error calling dARK minter", null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DarkMinterClientException(500, request.method(), request.uri(), "Interrupted while calling dARK minter", null, e);
        }
    }

    private URI buildUri(String path) {
        String baseUrl = properties.getMinter().getBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + path);
    }

    private String authHeaderName() {
        return normalizeRequired(properties.getAuthHeaderName(), "authHeaderName");
    }

    private String normalizeRequired(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new DarkMinterClientException(500, label + " must not be blank");
        }
        return normalized;
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new DarkMinterClientException(500, "Unable to serialize dARK request", e);
        }
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Empty error response from dARK minter";
        }

        try {
            return objectMapper.readTree(body).path("detail").asText(body);
        } catch (IOException e) {
            return body;
        }
    }
}
