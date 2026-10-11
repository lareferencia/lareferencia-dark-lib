package org.lareferencia.contrib.dark.services;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** DARK2 wire protocol: ordinary Ed25519 over eight UTF-8 lines, without a trailing LF. */
public final class DarkRequestSigner {
    private DarkRequestSigner() { }

    public static Map<String, String> sign(PrivateKey key, String keyId, String method, URI uri,
            String authority, byte[] body, long timestamp, UUID nonce) {
        String path = uri.getRawPath();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) path += "?" + uri.getRawQuery();
        String digest = sha256(body);
        String time = Long.toString(timestamp);
        String nonceText = nonce.toString();
        String[] lines = {"DARK2", method.toUpperCase(Locale.ROOT), path, authority, keyId, time, nonceText, digest};
        for (String line : lines) {
            if (line == null || line.isBlank() || line.contains("\r") || line.contains("\n") || !line.equals(line.trim()))
                throw new IllegalArgumentException("Invalid DARK2 canonical field");
        }
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            return Map.of("X-DARK-Authority-Id", authority, "X-DARK-Key-Id", keyId,
                    "X-DARK-Timestamp", time, "X-DARK-Nonce", nonceText, "X-DARK-Content-SHA256", digest,
                    "X-DARK-Signature", Base64.getEncoder().encodeToString(signature.sign()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign DARK2 request", e);
        }
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
