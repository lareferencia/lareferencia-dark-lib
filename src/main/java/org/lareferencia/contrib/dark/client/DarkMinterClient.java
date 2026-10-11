package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.contrib.dark.services.DarkProperties;
import org.lareferencia.contrib.dark.services.DarkArkIdentifier;
import org.lareferencia.contrib.dark.services.DarkCredentialService;
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
    private static final String DARK_ERROR_CODE_HEADER = "X-DARK-Error-Code";
    private static final String DARK_RETRYABLE_HEADER = "X-DARK-Retryable";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DarkProperties properties;
    private final DarkCredentialService credentials;

    @Autowired
    public DarkMinterClient(DarkProperties properties, DarkCredentialService credentials) {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), new ObjectMapper(), properties, credentials);
    }

    DarkMinterClient(HttpClient httpClient, ObjectMapper objectMapper, DarkProperties properties) {
        this(httpClient, objectMapper, properties, null);
    }

    DarkMinterClient(HttpClient httpClient, ObjectMapper objectMapper, DarkProperties properties, DarkCredentialService credentials) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.credentials = credentials;
    }

    public ReserveBatchResponse reserveBatch(String authorityId, String naan, List<String> clientItemIds) {
        String normalizedAuthorityId = normalizeRequired(authorityId, "authorityId");
        String normalizedNaan = normalizeRequired(naan, "naan");
        ReserveBatchRequest request = ReserveBatchRequest.fromClientItemIds(normalizedAuthorityId, normalizedNaan, clientItemIds);
        return sendModification("POST", "/api/v1/arks/batch", normalizedAuthorityId, request, ReserveBatchResponse.class);
    }

    public ARKResponse stageArk(String ark, StageArkRequest request) {
        String normalizedAuthorityId = normalizeRequired(request.getAuthorityId(), "authorityId");
        request.setAuthorityId(normalizedAuthorityId);
        return sendModification("PUT", "/api/v1/arks/" + DarkArkIdentifier.normalize(ark), normalizedAuthorityId, request, ARKResponse.class);
    }

    private <T> T sendModification(String method, String path, String authority, Object payload, Class<T> responseType) {
        synchronized (properties) {
            var auth = properties.getMinter().getAuthentication();
            requireModificationsEnabled();
            URI uri = buildUri(path);
            byte[] body;
            try { body = objectMapper.writeValueAsBytes(payload); }
            catch (IOException e) { throw new DarkMinterClientException(500, "Unable to serialize dARK request", e); }
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            if ("dark2".equals(auth.getMode())) {
                if (credentials == null) throw new DarkMinterClientException(503, "DARK_CREDENTIAL_UNAVAILABLE", false, "Signing service unavailable");
                credentials.headers(method, uri, authority, body).forEach(builder::header);
                // Minters with signature verification disabled use this explicit identity header.
                builder.header("X-Authority-Id", authority);
                // One send only. A lost response requires business reconciliation, not another signed attempt.
                try {
                    return sendJsonRequestOnce(builder.build(), responseType);
                } catch (DarkMinterClientException e) {
                    if (e.isRetryable() || "CLIENT_INTERRUPTED".equals(e.getErrorCode())
                            || "SIGNED_REQUEST_REPLAY".equals(e.getErrorCode())) {
                        throw new DarkMinterClientException(e.getStatusCode(), method, uri,
                                DarkMinterClientException.MODIFICATION_OUTCOME_UNKNOWN, true,
                                "Modification outcome is unknown; reconcile before resending (" + e.getErrorCode() + ")",
                                e.getResponseBody(), e);
                    }
                    throw e;
                }
            }
            builder.header(authHeaderName(), authority);
            if (!"legacy".equals(auth.getMode()))
                throw new DarkMinterClientException(503, "DARK_AUTHENTICATION_MODE_INVALID", false, "Unsupported authentication mode");
            return sendJsonRequest(builder, responseType);
        }
    }

    public void requireModificationsEnabled() {
        synchronized (properties) {
            var auth = properties.getMinter().getAuthentication();
            if ("pending_registration".equals(auth.getRegistrationStatus())
                    || ("dark2".equals(auth.getMode()) && !"enabled".equals(auth.getRegistrationStatus())))
                throw new DarkMinterClientException(503, "DARK_AUTHENTICATION_PAUSED", false,
                        "Minter modifications are paused pending public key registration");
            if (!java.util.Set.of("legacy", "dark2").contains(auth.getMode()))
                throw new DarkMinterClientException(503, "DARK_AUTHENTICATION_MODE_INVALID", false, "Unsupported authentication mode");
        }
    }

    public ARKResponse getArk(String ark) {
        return sendJsonRequest(
                HttpRequest.newBuilder(buildUri("/api/v1/arks/" + DarkArkIdentifier.normalize(ark)))
                        .GET(),
                ARKResponse.class);
    }

    public ArkStatusBatchResponse getArkStatuses(List<String> arks) {
        if (arks == null || arks.isEmpty() || arks.size() > 100) {
            throw new IllegalArgumentException("dARK status batch requires between 1 and 100 ARKs");
        }
        ArkStatusBatchRequest request = ArkStatusBatchRequest.fromArks(arks);
        return sendJsonRequest(
                HttpRequest.newBuilder(buildUri("/api/v1/arks/status/batch"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(writeJson(request))),
                ArkStatusBatchResponse.class);
    }

    private <T> T sendJsonRequest(HttpRequest.Builder builder, Class<T> responseType) {
        HttpRequest request = builder.timeout(REQUEST_TIMEOUT).build();
        int maxRetries = properties.getMinter().getRetry().getMaxRetries();
        DarkMinterClientException lastException = null;

        for (int retry = 0; retry <= maxRetries; retry++) {
            try {
                return sendJsonRequestOnce(request, responseType);
            } catch (DarkMinterClientException e) {
                lastException = e;
                if (!shouldRetry(e, retry, maxRetries)) {
                    throw e;
                }

                Duration delay = retryDelay(retry);
                logger.warn(
                        "dARK minter retryable error on [{} {}] | status={} | errorCode={} | retry={}/{} | retryInSeconds={} | message={}",
                        request.method(),
                        request.uri(),
                        e.getStatusCode(),
                        e.getErrorCode(),
                        retry + 1,
                        maxRetries,
                        delay.toSeconds(),
                        e.getMessage());
                sleepBeforeRetry(delay, request);
            }
        }

        throw lastException;
    }

    private <T> T sendJsonRequestOnce(HttpRequest request, Class<T> responseType) {
        logger.debug("Calling dARK minter [{} {}]", request.method(), request.uri());

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            logger.debug("dARK minter responded [{}] {}", response.statusCode(), body);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorMessage = extractErrorMessage(body);
                String errorCode = extractErrorCode(response);
                boolean retryable = extractRetryable(response) && !errorCode.startsWith("SIGNED_")
                        && !errorCode.equals("AUTHORITY_MISMATCH");
                logger.warn("dARK minter rejected request [{} {}] with status {}, errorCode={}, retryable={} and body {}",
                        request.method(), request.uri(), response.statusCode(), errorCode, retryable, body);
                throw new DarkMinterClientException(
                        response.statusCode(),
                        request.method(),
                        request.uri(),
                        errorCode,
                        retryable,
                        errorMessage,
                        body);
            }

            if (body == null || body.isBlank()) {
                throw new DarkMinterClientException(
                        502,
                        request.method(),
                        request.uri(),
                        "EMPTY_RESPONSE",
                        true,
                        "Empty success response from dARK minter",
                        body);
            }
            T parsed = objectMapper.readValue(body, responseType);
            if (parsed == null) {
                throw new DarkMinterClientException(
                        502,
                        request.method(),
                        request.uri(),
                        "INVALID_RESPONSE",
                        true,
                        "Null JSON response from dARK minter",
                        body);
            }
            return parsed;
        } catch (IOException e) {
            throw new DarkMinterClientException(
                    500,
                    request.method(),
                    request.uri(),
                    "CLIENT_IO_ERROR",
                    true,
                    "I/O error calling dARK minter",
                    null,
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DarkMinterClientException(
                    500,
                    request.method(),
                    request.uri(),
                    "CLIENT_INTERRUPTED",
                    true,
                    "Interrupted while calling dARK minter",
                    null,
                    e);
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

    private boolean shouldRetry(DarkMinterClientException e, int retry, int maxRetries) {
        return e.isRetryable()
                && retry < maxRetries
                && !Thread.currentThread().isInterrupted();
    }

    private Duration retryDelay(int retry) {
        List<Long> backoffSeconds = properties.getMinter().getRetry().getBackoffSeconds();
        int delayIndex = Math.min(retry, backoffSeconds.size() - 1);
        return Duration.ofSeconds(Math.max(0L, backoffSeconds.get(delayIndex)));
    }

    private void sleepBeforeRetry(Duration delay, HttpRequest request) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }

        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DarkMinterClientException(
                    500,
                    request.method(),
                    request.uri(),
                    "CLIENT_INTERRUPTED",
                    true,
                    "Interrupted while waiting to retry dARK minter request",
                    null,
                    e);
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

    private String extractErrorCode(HttpResponse<String> response) {
        return response.headers()
                .firstValue(DARK_ERROR_CODE_HEADER)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(DarkMinterClientException.UNKNOWN_ERROR_CODE);
    }

    private boolean extractRetryable(HttpResponse<String> response) {
        return response.headers()
                .firstValue(DARK_RETRYABLE_HEADER)
                .map(value -> value.equalsIgnoreCase("true"))
                .orElse(response.statusCode() >= 500);
    }
}
