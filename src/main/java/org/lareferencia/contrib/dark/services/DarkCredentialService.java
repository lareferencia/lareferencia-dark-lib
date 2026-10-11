package org.lareferencia.contrib.dark.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/** Single-instance management. Uses the same monitor as configuration changes and signed sends. */
@Service
public class DarkCredentialService {
    private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(DarkCredentialService.class);
    public record Status(String mode, String registrationStatus, String credentialId, String keyId,
            String publicKey, String publicKeyBase64, String authorityId, String baseUrl, String storageRoot,
            String generatedAt, String generatedBy, String confirmedAt, String confirmedBy, String error) { }

    private final DarkProperties properties;
    private final DarkRuntimeConfigurationService configuration;
    private final DarkKeyStorage storage;
    private String cachedId;
    private String cachedRoot;
    private DarkKeyStorage.Material cached;

    public DarkCredentialService(DarkProperties properties, DarkRuntimeConfigurationService configuration, DarkKeyStorage storage) {
        this.properties = properties; this.configuration = configuration; this.storage = storage;
    }

    public Status status() {
        synchronized (properties) {
            JsonNode client = configuration.get().path("client");
            JsonNode auth = client.path("authentication");
            String id = auth.path("credentialId").asText(null);
            String ssh = null, raw = null, keyId = null, error = null;
            if (id != null) {
                try {
                    var material = storage.load(root(auth), properties.getKeyStorageRoot(), id);
                    ssh = material.publicKeySsh(); keyId = material.keyId();
                    raw = Base64.getEncoder().encodeToString(DarkKeyStorage.rawPublicKey(material.publicKey()));
                    if (!keyId.equals(auth.path("keyId").asText())) error = "Credential fingerprint differs from persisted configuration";
                } catch (RuntimeException e) { error = "Credential files are unavailable or invalid; check server storage and permissions"; }
            }
            return new Status(auth.path("mode").asText("legacy"), error == null ? auth.path("registrationStatus").asText("not_configured") : "unavailable",
                    id, keyId, ssh, raw, client.path("authorityId").asText(null), client.path("baseUrl").asText(null), root(auth),
                    auth.path("generatedAt").asText(null), auth.path("generatedBy").asText(null),
                    auth.path("confirmedAt").asText(null), auth.path("confirmedBy").asText(null), error);
        }
    }

    public Status generate(boolean confirmed, String expectedCredentialId, String updatedBy) {
        synchronized (properties) {
            if (!confirmed) throw new IllegalArgumentException("Explicit confirmation of the interruption is required");
            JsonNode client = configuration.get().path("client");
            JsonNode auth = client.path("authentication");
            checkId(auth, expectedCredentialId);
            if (!"dark2".equals(auth.path("mode").asText())) throw new IllegalArgumentException("Save DARK2 configuration before generating credentials");
            DarkRuntimeConfigurationService.validateSignedConfiguration(client);
            // Commit the pause first: failures and restarts cannot silently resume old credentials.
            configuration.mutateAuthentication(value -> {
                value.put("registrationStatus", "pending_registration");
                value.remove(List.of("confirmedAt", "confirmedBy"));
            }, updatedBy);
            String id = storage.generate(root(auth), properties.getKeyStorageRoot());
            var material = storage.load(root(auth), properties.getKeyStorageRoot(), id);
            configuration.mutateAuthentication(value -> {
                value.put("credentialId", id); value.put("keyId", material.keyId());
                value.put("authorityId", client.path("authorityId").asText());
                value.put("baseUrl", client.path("baseUrl").asText());
                value.put("generatedAt", Instant.now().toString()); value.put("generatedBy", updatedBy);
            }, updatedBy);
            cached = null;
            logger.info("dARK credentials generated | credentialId={} | keyId={} | updatedBy={} | registration=pending", id, material.keyId(), updatedBy);
            return status();
        }
    }

    public Status resume(String credentialId, String keyId, String authorityId, String baseUrl, boolean confirmed, String updatedBy) {
        synchronized (properties) {
            if (!confirmed) throw new IllegalArgumentException("Confirm registration by the minter administrator before resuming");
            JsonNode client = configuration.get().path("client");
            JsonNode auth = client.path("authentication");
            checkId(auth, credentialId);
            if (!Objects.equals(authorityId, client.path("authorityId").asText()) || !Objects.equals(baseUrl, client.path("baseUrl").asText()))
                throw new IllegalArgumentException("Authority or destination changed; refresh and confirm registration again");
            if (credentialId == null || !"dark2".equals(auth.path("mode").asText())) throw new IllegalArgumentException("DARK2 credentials are required");
            DarkRuntimeConfigurationService.validateSignedDestination(client);
            var material = storage.load(root(auth), properties.getKeyStorageRoot(), credentialId);
            if (!material.keyId().equals(keyId) || !material.keyId().equals(auth.path("keyId").asText()))
                throw new IllegalArgumentException("Confirm the fingerprint of the current credential");
            // Confirmation applies to the currently displayed authority/destination, including edits.
            configuration.mutateAuthentication(value -> {
                value.put("authorityId", client.path("authorityId").asText());
                value.put("baseUrl", client.path("baseUrl").asText());
                value.put("registrationStatus", "enabled");
                value.put("confirmedAt", Instant.now().toString()); value.put("confirmedBy", updatedBy);
            }, updatedBy);
            cached = material; cachedId = credentialId; cachedRoot = root(auth);
            logger.info("dARK registration confirmed and modifications resumed | credentialId={} | keyId={} | updatedBy={}", credentialId, keyId, updatedBy);
            return status();
        }
    }

    /** Caller holds the properties monitor through the HTTP send, so replacement drains in-flight writes. */
    public Map<String, String> headers(String method, URI uri, String authority, byte[] body) {
        synchronized (properties) {
            var auth = properties.getMinter().getAuthentication();
            if (!"enabled".equals(auth.getRegistrationStatus()))
                throw new DarkMinterClientException(503, "DARK_AUTHENTICATION_PAUSED", false, "Minter modifications are paused pending public key registration");
            if (!Objects.equals(authority, properties.getAuthorityId()) || !Objects.equals(authority, auth.getAuthorityId())
                    || !Objects.equals(properties.getMinter().getBaseUrl(), auth.getBaseUrl()))
                throw new DarkMinterClientException(503, "DARK_AUTHENTICATION_CONFIGURATION_CHANGED", false, "Confirm registration for the current authority and destination");
            try {
                storage.requireAvailable(properties.getKeyStorageRoot(), properties.getKeyStorageRoot(), auth.getCredentialId());
                if (cached == null || !Objects.equals(cachedId, auth.getCredentialId()) || !Objects.equals(cachedRoot, properties.getKeyStorageRoot())) {
                    cached = storage.load(properties.getKeyStorageRoot(), properties.getKeyStorageRoot(), auth.getCredentialId());
                    cachedId = auth.getCredentialId(); cachedRoot = properties.getKeyStorageRoot();
                }
                if (!Objects.equals(cached.keyId(), auth.getKeyId())) throw new IllegalStateException("Credential fingerprint mismatch");
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
                    throw new IllegalStateException("DARK2 requires HTTP or HTTPS");
                return DarkRequestSigner.sign(cached.privateKey(), cached.keyId(), method, uri, authority, body,
                        Instant.now().getEpochSecond(), UUID.randomUUID());
            } catch (RuntimeException e) {
                throw new DarkMinterClientException(503, method, uri, "DARK_CREDENTIAL_UNAVAILABLE", false,
                        "Unable to load or use server signing credentials", null, e);
            }
        }
    }

    private String root(JsonNode auth) { return properties.getKeyStorageRoot(); }
    private void checkId(JsonNode auth, String expected) {
        if (!Objects.equals(auth.path("credentialId").asText(null), expected))
            throw new IllegalArgumentException("Credentials changed; refresh before continuing");
    }
}
