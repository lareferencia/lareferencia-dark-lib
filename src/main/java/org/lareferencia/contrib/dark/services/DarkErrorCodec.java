package org.lareferencia.contrib.dark.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes the current dARK tracking error without changing the tracking schema.
 */
@Component
public class DarkErrorCodec {

    private static final int VERSION = 1;
    private static final int MAX_MESSAGE_LENGTH = 1200;
    private final ObjectMapper objectMapper;

    public DarkErrorCodec() {
        this(new ObjectMapper());
    }

    DarkErrorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(Exception error, String phase) {
        String category = "INTERNAL";
        String code = error.getClass().getSimpleName();
        Integer httpStatus = null;
        boolean retryable = false;

        if (error instanceof DarkMinterClientException minterError) {
            httpStatus = minterError.getStatusCode();
            code = minterError.getErrorCode();
            retryable = minterError.isRetryable();
            category = categoryForStatus(httpStatus, retryable);
        } else if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            category = "VALIDATION";
        }

        return encode(category, code, phase, httpStatus, retryable, error.getMessage(), Map.of());
    }

    public String encode(String category, String code, String phase, Integer httpStatus,
            boolean retryable, String message, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", VERSION);
        payload.put("category", category);
        payload.put("code", code == null || code.isBlank() ? "UNKNOWN" : code);
        payload.put("phase", phase);
        payload.put("httpStatus", httpStatus);
        payload.put("retryable", retryable);
        payload.put("message", boundedMessage(message));
        payload.put("details", details == null ? Map.of() : details);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException serializationError) {
            return "{\"version\":1,\"category\":\"INTERNAL\",\"code\":\"ERROR_SERIALIZATION_FAILED\",\"retryable\":false,\"message\":\"Unable to serialize dARK error\"}";
        }
    }

    private String categoryForStatus(int status, boolean retryable) {
        if (status == 401 || status == 403) return "AUTHORIZATION";
        if (status == 409) return "CONFLICT";
        if (status == 413) return "PAYLOAD_TOO_LARGE";
        if (status == 429) return "RATE_LIMIT";
        if (retryable || status >= 500 || status == 408) return "REMOTE_TRANSIENT";
        return "REMOTE_PERMANENT";
    }

    private String boundedMessage(String message) {
        String normalized = message == null || message.isBlank() ? "Unknown dARK error" : message.trim();
        return normalized.length() <= MAX_MESSAGE_LENGTH ? normalized : normalized.substring(0, MAX_MESSAGE_LENGTH);
    }
}
