package org.lareferencia.contrib.dark.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DarkErrorCodecTest {

    private final DarkErrorCodec codec = new DarkErrorCodec();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void encodesPayloadTooLargeAsStructuredError() throws Exception {
        String json = codec.encode(new DarkMinterClientException(413, "PAYLOAD_TOO_LARGE", false, "Payload exceeds limit"), "STAGE");
        JsonNode result = mapper.readTree(json);

        assertEquals(1, result.path("version").asInt());
        assertEquals("PAYLOAD_TOO_LARGE", result.path("category").asText());
        assertEquals(413, result.path("httpStatus").asInt());
        assertFalse(result.path("retryable").asBoolean());
    }

    @Test
    void encodesValidationErrors() throws Exception {
        JsonNode result = mapper.readTree(codec.encode(new IllegalArgumentException("Missing dc.title"), "BUILD_L1"));

        assertEquals("VALIDATION", result.path("category").asText());
        assertEquals("BUILD_L1", result.path("phase").asText());
    }
}
