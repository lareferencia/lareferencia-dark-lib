package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DarkRequestSignerTest {
    @Test
    void matchesPublishedDark2InteroperabilityVector() throws Exception {
        byte[] encoded = HexFormat.of().parseHex("302e020100300506032b657004220420000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        var key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        byte[] body = "{\"authority_id\":\"11111111-1111-4111-8111-111111111111\",\"naan\":\"12345\"}".getBytes(StandardCharsets.UTF_8);
        var headers = DarkRequestSigner.sign(key, "56475aa75463474c0285df5dbf2bcab73da651358839e9b77481b2eab107708c", "POST",
                URI.create("https://example.org/api/v1/arks"), "11111111-1111-4111-8111-111111111111", body,
                1700000000, UUID.fromString("22222222-2222-4222-8222-222222222222"));
        assertEquals("b4625ce6bb01deb70ccc17b509765e81040ba2307c8921fecce86ca53e2f9cc0", headers.get("X-DARK-Content-SHA256"));
        assertEquals("6sEEbLK0VBs69+R4WrWzK4SXickUEYqAtreVoigl1Clq4g2SgAgOGPKBn0vLS/tqm8MpHyPpCILspzRF9hlIAQ==", headers.get("X-DARK-Signature"));
        assertEquals(6, headers.size());
    }
}
