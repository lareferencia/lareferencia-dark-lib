package org.lareferencia.contrib.dark.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.lareferencia.contrib.dark.domain.DarkRuntimeConfiguration;
import org.lareferencia.contrib.dark.repositories.DarkRuntimeConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Objects;
import java.util.function.Consumer;
import jakarta.annotation.PostConstruct;

/** Persists and applies dARK settings without changing the legacy network model. */
@Service
public class DarkRuntimeConfigurationService {
    public static final long CONFIGURATION_ID = 1L;
    private final DarkRuntimeConfigurationRepository repository;
    private final ObjectMapper mapper;
    private final DarkProperties properties;
    private final TransactionTemplate transactions;

    public DarkRuntimeConfigurationService(DarkRuntimeConfigurationRepository repository, ObjectMapper mapper,
            DarkProperties properties, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PostConstruct
    void loadPersistedConfiguration() {
        repository.findById(CONFIGURATION_ID).ifPresent(row -> {
            if (row.getConfiguration() != null) apply(row.getConfiguration());
        });
    }

    public JsonNode get() {
        synchronized (properties) {
            DarkRuntimeConfiguration row = repository.findById(CONFIGURATION_ID).orElse(null);
            ObjectNode value = row == null || row.getConfiguration() == null ? (ObjectNode) defaults() : (ObjectNode) row.getConfiguration().deepCopy();
            ObjectNode auth = authentication(value);
            auth.put("storageRoot", properties.getKeyStorageRoot());
            ((ObjectNode) value.path("client")).set("authentication", auth);
            return value;
        }
    }

    public JsonNode replace(JsonNode value, String updatedBy) {
        synchronized (properties) {
            validate(value);
            ObjectNode next = (ObjectNode) value.deepCopy();
            ObjectNode currentAuth = authentication((ObjectNode) get());
            ObjectNode client = (ObjectNode) next.path("client");
            if (client.has("authentication") && !client.path("authentication").isObject())
                throw new IllegalArgumentException("client.authentication must be an object");
            ObjectNode requestedAuth = client.path("authentication").isObject()
                    ? (ObjectNode) client.path("authentication").deepCopy() : mapper.createObjectNode();
            java.util.Set<String> allowed = java.util.Set.of("mode", "storageRoot", "credentialId", "registrationStatus",
                    "authorityId", "baseUrl", "generatedAt", "generatedBy", "confirmedAt", "confirmedBy", "keyId");
            requestedAuth.fieldNames().forEachRemaining(name -> {
                if (!allowed.contains(name)) throw new IllegalArgumentException("Unknown authentication field: " + name);
            });
            for (String name : java.util.List.of("credentialId", "registrationStatus", "authorityId", "baseUrl",
                    "generatedAt", "generatedBy", "confirmedAt", "confirmedBy", "keyId")) {
                if (requestedAuth.has(name) && !Objects.equals(requestedAuth.get(name), currentAuth.get(name)))
                    throw new IllegalArgumentException("Authentication lifecycle fields are managed by the credential endpoints");
            }
            ObjectNode merged = currentAuth.deepCopy();
            if (requestedAuth.has("mode")) merged.set("mode", requestedAuth.get("mode"));
            if (requestedAuth.has("storageRoot") && !properties.getKeyStorageRoot().equals(requestedAuth.path("storageRoot").asText()))
                throw new IllegalArgumentException("Key storage is fixed at store.basepath/keys and cannot be selected");
            merged.put("storageRoot", properties.getKeyStorageRoot());
            String mode = merged.path("mode").asText("legacy");
            if (!java.util.Set.of("legacy", "dark2").contains(mode)) throw new IllegalArgumentException("Invalid authentication mode");
            DarkKeyStorage.validateRoot(merged.path("storageRoot").asText(properties.getKeyStorageRoot()), properties.getKeyStorageRoot());
            if ("pending_registration".equals(currentAuth.path("registrationStatus").asText()) && !mode.equals("dark2"))
                throw new IllegalArgumentException("Registration pending: switching to legacy cannot bypass the pause");
            if (!merged.path("credentialId").asText("").isBlank()) {
                if (!mode.equals("dark2")) throw new IllegalArgumentException("Managed credentials require DARK2");
                if (!Objects.equals(client.path("authorityId").asText(), merged.path("authorityId").asText())
                        || !Objects.equals(client.path("baseUrl").asText(), merged.path("baseUrl").asText())) {
                    merged.put("registrationStatus", "pending_registration");
                    merged.remove(java.util.List.of("confirmedAt", "confirmedBy"));
                }
            }
            if (mode.equals("dark2")) validateSignedConfiguration(client);
            client.set("authentication", merged);
            return save(next, updatedBy);
        }
    }

    /** Called under the same monitor as modifications; commit precedes publication in memory. */
    JsonNode mutateAuthentication(Consumer<ObjectNode> mutation, String updatedBy) {
        synchronized (properties) {
            ObjectNode next = (ObjectNode) get();
            ObjectNode auth = authentication(next);
            mutation.accept(auth);
            ((ObjectNode) next.path("client")).set("authentication", auth);
            return save(next, updatedBy);
        }
    }

    private JsonNode save(ObjectNode next, String updatedBy) {
        transactions.executeWithoutResult(status -> {
            DarkRuntimeConfiguration row = repository.findById(CONFIGURATION_ID).orElseGet(DarkRuntimeConfiguration::new);
            row.setId(CONFIGURATION_ID);
            row.setConfiguration(next.deepCopy());
            row.setUpdatedBy(updatedBy);
            repository.saveAndFlush(row);
        });
        apply(next);
        return next.deepCopy();
    }

    private ObjectNode authentication(ObjectNode root) {
        JsonNode auth = root.path("client").path("authentication");
        return auth.isObject() ? (ObjectNode) auth.deepCopy() : defaultAuthentication();
    }

    private ObjectNode defaultAuthentication() {
        ObjectNode auth = mapper.createObjectNode();
        auth.put("mode", properties.getMinter().getAuthentication().getMode());
        auth.put("storageRoot", properties.getKeyStorageRoot());
        auth.putNull("credentialId");
        auth.put("registrationStatus", "not_configured");
        return auth;
    }

    static void validateSignedDestination(JsonNode client) {
        validateSignedConfiguration(client);
        if (client.path("authorityId").asText().isBlank())
            throw new IllegalArgumentException("DARK2 requires an authority ID before enabling modifications");
    }

    static void validateSignedConfiguration(JsonNode client) {
        java.net.URI uri = java.net.URI.create(client.path("baseUrl").asText());
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("DARK2 requires an HTTP or HTTPS base URL without credentials, query or fragment");
        String authority = client.path("authorityId").asText();
        if (!authority.equals(authority.trim()) || authority.contains("\n") || authority.contains("\r"))
            throw new IllegalArgumentException("DARK2 requires an exact authority ID without whitespace or line breaks");
    }

    public JsonNode defaults() {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode client = root.putObject("client");
        client.put("baseUrl", properties.getMinter().getBaseUrl());
        client.put("authorityId", properties.getAuthorityId());
        client.put("authHeaderName", properties.getAuthHeaderName());
        client.set("authentication", defaultAuthentication());
        client.put("maxRetries", properties.getMinter().getRetry().getMaxRetries());
        ArrayNode backoff = client.putArray("backoffSeconds");
        properties.getMinter().getRetry().getBackoffSeconds().forEach(backoff::add);
        ObjectNode stage = root.putObject("stage");
        stage.put("metadataSchema", properties.getMetadataSchema());
        stage.put("metadataMediaType", properties.getMetadataMediaType());
        stage.put("pageSize", properties.getStagePageSize());
        stage.put("maxPagesPerRun", properties.getStageMaxPagesPerRun());
        stage.put("reserveBatchSize", properties.getReserveBatchSize());
        root.putObject("reconcile").put("pageSize", properties.getReconcilePageSize());
        return root;
    }

    private void apply(JsonNode value) {
        ObjectNode client = object(value, "client");
        ObjectNode stage = object(value, "stage");
        ObjectNode reconcile = object(value, "reconcile");
        properties.applyRuntimeConfiguration(client, stage, reconcile);
    }

    private ObjectNode object(JsonNode node, String name) {
        JsonNode child = node.path(name);
        return child.isObject() ? (ObjectNode) child : mapper.createObjectNode();
    }

    private void validate(JsonNode value) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("dARK configuration must be a JSON object");
        if (!value.path("client").isObject() || !value.path("stage").isObject() || !value.path("reconcile").isObject())
            throw new IllegalArgumentException("dARK configuration requires client, stage and reconcile objects");
        if (value.path("client").path("baseUrl").asText().isBlank()) throw new IllegalArgumentException("client.baseUrl must not be blank");
        if (value.path("stage").path("pageSize").asInt(0) < 1 || value.path("reconcile").path("pageSize").asInt(0) < 1)
            throw new IllegalArgumentException("page sizes must be greater than zero");
        if (value.path("stage").path("reserveBatchSize").asInt(0) < 1) throw new IllegalArgumentException("stage.reserveBatchSize must be greater than zero");
    }
}
