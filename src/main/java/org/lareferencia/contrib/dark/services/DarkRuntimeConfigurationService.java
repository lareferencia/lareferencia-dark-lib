package org.lareferencia.contrib.dark.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.lareferencia.contrib.dark.domain.DarkRuntimeConfiguration;
import org.lareferencia.contrib.dark.repositories.DarkRuntimeConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

/** Persists and applies dARK settings without changing the legacy network model. */
@Service
public class DarkRuntimeConfigurationService {
    public static final long CONFIGURATION_ID = 1L;
    private final DarkRuntimeConfigurationRepository repository;
    private final ObjectMapper mapper;
    private final DarkProperties properties;
    private volatile JsonNode applied;

    public DarkRuntimeConfigurationService(DarkRuntimeConfigurationRepository repository, ObjectMapper mapper,
            DarkProperties properties) {
        this.repository = repository;
        this.mapper = mapper;
        this.properties = properties;
    }

    @PostConstruct
    void loadPersistedConfiguration() {
        repository.findById(CONFIGURATION_ID).ifPresent(row -> {
            if (row.getConfiguration() != null) apply(row.getConfiguration());
        });
    }

    @Transactional(readOnly = true)
    public JsonNode get() {
        DarkRuntimeConfiguration row = repository.findById(CONFIGURATION_ID).orElse(null);
        JsonNode value = row == null || row.getConfiguration() == null ? defaults() : row.getConfiguration().deepCopy();
        apply(value);
        return value;
    }

    @Transactional
    public JsonNode replace(JsonNode value, String updatedBy) {
        validate(value);
        DarkRuntimeConfiguration row = repository.findById(CONFIGURATION_ID).orElseGet(DarkRuntimeConfiguration::new);
        row.setId(CONFIGURATION_ID);
        row.setConfiguration(value.deepCopy());
        row.setUpdatedBy(updatedBy);
        repository.save(row);
        apply(value);
        return value.deepCopy();
    }

    public JsonNode defaults() {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode client = root.putObject("client");
        client.put("baseUrl", properties.getMinter().getBaseUrl());
        client.put("authorityId", properties.getAuthorityId());
        client.put("authHeaderName", properties.getAuthHeaderName());
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
        applied = value.deepCopy();
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
