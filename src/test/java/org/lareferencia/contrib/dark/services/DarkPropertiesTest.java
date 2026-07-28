package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("DarkProperties binding tests")
class DarkPropertiesTest {

    @Test
    @DisplayName("Bind documented nested worker settings")
    void bindsNestedWorkerSettings() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "dark.stage.page-size", "25",
                "dark.stage.max-pages-per-run", "3",
                "dark.reserve.batch-size", "40",
                "dark.reconcile.page-size", "50"));

        DarkProperties properties = new Binder(source)
                .bind("dark", DarkProperties.class)
                .orElseThrow(() -> new AssertionError("dark properties were not bound"));

        assertEquals(25, properties.getStagePageSize());
        assertEquals(3, properties.getStageMaxPagesPerRun());
        assertEquals(40, properties.getReserveBatchSize());
        assertEquals(50, properties.getReconcilePageSize());
    }
}
