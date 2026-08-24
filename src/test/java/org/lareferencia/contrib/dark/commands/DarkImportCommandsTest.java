package org.lareferencia.contrib.dark.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkImportCommandsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesByDefaultWithoutPersisting() throws Exception {
        AtomicReference<DarkTrackingRecord> saved = new AtomicReference<>();
        DarkTrackingRepository repository = emptyRepository(saved);
        Path csv = writeLegacyCsv();

        String result = new DarkImportCommands(repository).importLegacyCsv(csv.toString(), false);

        assertTrue(result.contains("Dry run successful: 1 rows would be imported as UPDATE"));
        assertNull(saved.get());
    }

    @Test
    void importsExistingArksAsUpdateWithoutMetadataHashes() throws Exception {
        AtomicReference<DarkTrackingRecord> saved = new AtomicReference<>();
        DarkTrackingRepository repository = emptyRepository(saved);
        Path csv = writeLegacyCsv();

        String result = new DarkImportCommands(repository).importLegacyCsv(csv.toString(), true);

        DarkTrackingRecord record = saved.get();
        assertEquals("41046", record.getArkNaan());
        assertEquals("oai:repositorio.ufrn.br:123456789/46761", record.getOaiId());
        assertEquals("ark:/41046/001300001kq89", record.getArk());
        assertEquals(DarkTrackingState.UPDATE, record.getState());
        assertNull(record.getStagePayloadHash());
        assertNull(record.getLastStagedAt());
        assertTrue(result.contains("Imported 1 legacy dARK mappings"));
    }

    private DarkTrackingRepository emptyRepository(AtomicReference<DarkTrackingRecord> saved) {
        return (DarkTrackingRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DarkTrackingRepository.class},
                (proxy, method, arguments) -> {
                    if ("findById".equals(method.getName()) || "findByArk".equals(method.getName())) {
                        return Optional.empty();
                    }
                    if ("save".equals(method.getName())) {
                        saved.set((DarkTrackingRecord) arguments[0]);
                        return arguments[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private Path writeLegacyCsv() throws Exception {
        Path csv = temporaryDirectory.resolve("dark.csv");
        Files.writeString(csv,
                "darkidentifier,oaiidentifier,datestamp,itemurl,lastmodified\n"
                        + "ark:/41046/001300001kq89,oai:repositorio.ufrn.br:123456789/46761,"
                        + "2025-07-31 12:28:05.320471,https://repositorio.ufrn.br/handle/123456789/46761,"
                        + "2025-09-27 00:18:26.628545\n");
        return csv;
    }
}
