package org.lareferencia.contrib.dark.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lareferencia.contrib.dark.domain.DarkRuntimeConfiguration;
import org.lareferencia.contrib.dark.repositories.DarkRuntimeConfigurationRepository;
import org.lareferencia.core.domain.Transformer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DarkL1TransformerConfigurationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DarkProperties properties = new DarkProperties();
    private final DarkRuntimeConfigurationRepository repository = mock(DarkRuntimeConfigurationRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private DarkRuntimeConfiguration row;
    private DarkRuntimeConfigurationService service;

    @BeforeEach void setup() {
        when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(repository.findById(1L)).thenAnswer(call -> Optional.ofNullable(row));
        when(repository.saveAndFlush(any())).thenAnswer(call -> row = call.getArgument(0));
        service = new DarkRuntimeConfigurationService(repository, mapper, properties, tx);
        ReflectionTestUtils.setField(service, "entityManager", em);
    }

    @Test void oldConfigurationSelectionRestartAndExplicitReset() {
        ObjectNode config = (ObjectNode) service.defaults();
        ObjectNode stage = (ObjectNode) config.path("stage");
        stage.remove("l1TransformerId");
        row = new DarkRuntimeConfiguration();
        row.setConfiguration(config.deepCopy());
        service.loadPersistedConfiguration();
        assertNull(properties.getL1TransformerId());
        assertTrue(service.get().at("/stage/l1TransformerId").isNull());

        when(em.find(Transformer.class, 7L, LockModeType.PESSIMISTIC_READ)).thenReturn(new Transformer());
        stage.put("l1TransformerId", 7);
        service.replace(config, "admin");
        assertTrue(service.isL1TransformerSelected(7L));
        var restarted = new DarkProperties();
        new DarkRuntimeConfigurationService(repository, mapper, restarted, tx).loadPersistedConfiguration();
        assertEquals(7L, restarted.getL1TransformerId());

        stage.remove("l1TransformerId"); // Older clients may send a full configuration without this field.
        assertEquals(7, service.replace(config, "admin").at("/stage/l1TransformerId").intValue());
        stage.putNull("l1TransformerId");
        assertTrue(service.replace(config, "admin").at("/stage/l1TransformerId").isNull());
        assertNull(properties.getL1TransformerId());
        assertFalse(service.isL1TransformerSelected(7L));
    }

    @Test void rejectsInvalidOrMissingTransformerBeforePersisting() throws Exception {
        ObjectNode config = (ObjectNode) service.defaults();
        ObjectNode stage = (ObjectNode) config.path("stage");
        for (String json : java.util.List.of("0", "-1", "1.5", "\"7\"", "9223372036854775808", "999")) {
            stage.set("l1TransformerId", mapper.readTree(json));
            assertThrows(IllegalArgumentException.class, () -> service.replace(config, "admin"));
        }
        assertNull(row);
        assertNull(properties.getL1TransformerId());
    }
}
