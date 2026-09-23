package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DarkArkIdentifierTest {

    @Test
    void normalizesHistoricalSlashFormToCanonicalArk() {
        assertEquals("ark:12345/example", DarkArkIdentifier.normalize(" ark:12345/example "));
        assertEquals("ark:12345/example", DarkArkIdentifier.normalize("ark:12345/example"));
        assertNull(DarkArkIdentifier.normalize(null));
    }
}
