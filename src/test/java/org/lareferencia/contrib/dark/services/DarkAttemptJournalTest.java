package org.lareferencia.contrib.dark.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.lareferencia.contrib.dark.client.*;
import org.lareferencia.contrib.dark.domain.*;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class DarkAttemptJournalTest {
    private static final String URL = "https://minter.example", AUTH = "authority-1";
    private static final String ARK = "ark:12345/abc";
    private LocalContainerEntityManagerFactoryBean factory;
    private JdbcTemplate db;
    private JpaTransactionManager tx;
    private DarkTrackingRepository repository;
    private DarkAttemptJournal journal;

    @BeforeEach void setup() throws Exception {
        String url = System.getProperty("dark.test.jdbc-url");
        String schema = "dark_test_" + UUID.randomUUID().toString().replace("-", "");
        if (url != null) {
            new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "dark-test")).execute("create schema " + schema);
        }
        var ds = url == null
                ? new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
                : new DriverManagerDataSource(url + "?currentSchema=" + schema, "postgres", "dark-test");
        db = new JdbcTemplate(ds);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("org.lareferencia.contrib.dark.domain");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        String migration = Files.readString(Path.of("../lareferencia-shell/src/main/resources/db/migration/V5.0.0.18__Dark_minter_attempts.sql"));
        for (var statement : migration.split(";")) if (!statement.isBlank()) db.execute(statement);
        tx = new JpaTransactionManager(factory.getObject());
        repository = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory.getObject()))
                .getRepository(DarkTrackingRepository.class);
        journal = restarted();
    }

    private DarkAttemptJournal restarted() { return new DarkAttemptJournal(db, repository, tx, new DarkErrorCodec()); }

    @AfterEach void cleanup() {
        factory.destroy();
        db.execute("drop table if exists dark_minter_attempt");
        db.execute("drop table if exists dark_tracking_installation");
    }

    private DarkAttemptJournal.Batch batch(String ark) {
        return new DarkAttemptJournal.Batch("12345", List.of(new DarkAttemptJournal.Item("oai:1", ark,
                "hash-B", "source", "https://repository.example/item", 12L, 34L)));
    }

    private ARKResponse remote() {
        var value = new ARKResponse();
        value.setArk(ARK);
        value.setState(DarkRemoteState.PUBLISHED);
        value.setClientItemId("oai:1");
        return value;
    }

    private DarkTrackingRecord row() {
        return repository.findById(DarkTrackingRecordId.of("12345", "oai:1")).orElseThrow();
    }

    @Test void receiptsSurviveWholePageRollbackAndRecoverInOrderWithoutAnotherSend() {
        journal.prepare(URL, AUTH);
        new TransactionTemplate(tx).executeWithoutResult(page -> {
            journal.prepare(URL, AUTH);
            journal.send(URL, AUTH, "RESERVE", batch(null), () -> {
                var reserved = new ReserveBatchResponse();
                reserved.setResults(List.of(remote()));
                return reserved;
            });
            journal.send(URL, AUTH, "STAGE", batch(ARK), this::remote);
            page.setRollbackOnly();
        });
        assertEquals(0, repository.count());
        var next = restarted();
        next.prepare(URL, AUTH);
        assertEquals(ARK, row().getArk());
        assertEquals(DarkTrackingState.PUBLISHED, row().getState());
        assertEquals("hash-B", row().getStagePayloadHash());
        assertEquals(34L, row().getSourceSnapshotId());
        var confirmedAt = row().getLastStagedAt();
        next.prepare(URL, AUTH);
        assertEquals(confirmedAt, row().getLastStagedAt());
        assertEquals(2, db.queryForObject("select count(*) from dark_minter_attempt where applied", Integer.class));
    }

    @Test void lostStageResponseIsVisibleBeforeSendAndRecoveredAsUncertain() {
        journal.prepare(URL, AUTH);
        new TransactionTemplate(tx).executeWithoutResult(t -> {
            var record = new DarkTrackingRecord();
            record.setId(DarkTrackingRecordId.of("12345", "oai:1"));
            record.setArk(ARK);
            record.setStagePayloadHash("confirmed-A");
            repository.save(record);
        });
        // A second connection must see the attempt before the transport starts.
        var dataSource = db.getDataSource();
        assertThrows(SimulatedCrash.class, () -> new TransactionTemplate(tx).executeWithoutResult(page ->
            journal.send(URL, AUTH, "STAGE", batch(ARK), () -> {
                try (var connection = dataSource.getConnection();
                     var statement = connection.createStatement();
                     var rows = statement.executeQuery("select outcome from dark_minter_attempt")) {
                    assertTrue(rows.next());
                    assertEquals("UNKNOWN", rows.getString(1));
                } catch (java.sql.SQLException e) { throw new AssertionError(e); }
                throw new SimulatedCrash();
            })));
        restarted().prepare(URL, AUTH);
        assertEquals(ARK, row().getArk());
        assertEquals("confirmed-A", row().getStagePayloadHash());
        assertTrue(new DarkErrorCodec().hasUnknownOutcome(row().getLastError()));
    }

    @Test void interruptedReservationPreservesTheOriginalCorrelationId() {
        journal.prepare(URL, AUTH);
        assertThrows(SimulatedCrash.class, () -> new TransactionTemplate(tx).executeWithoutResult(page ->
                journal.send(URL, AUTH, "RESERVE", batch(null), () -> { throw new SimulatedCrash(); })));
        restarted().prepare(URL, AUTH);
        assertEquals("oai:1", row().getOaiId());
        assertNull(row().getArk());
        assertTrue(new DarkErrorCodec().hasUnknownOutcome(row().getLastError()));
    }

    @Test void refusesRebindingAndDoesNotGuessWhoOwnsHistoricalTracking() {
        new TransactionTemplate(tx).executeWithoutResult(t -> {
            var record = new DarkTrackingRecord();
            record.setId(DarkTrackingRecordId.of("12345", "old"));
            repository.save(record);
        });
        assertThrows(IllegalStateException.class, () -> journal.prepare(URL, AUTH));
        db.update("update dark_tracking_installation set installation_id=?,base_url=?,authority_id=? where id=1",
                UUID.randomUUID(), URL, AUTH);
        journal.prepare(URL + "/", AUTH);
        assertThrows(IllegalArgumentException.class, () -> journal.prepare(URL, "authority-2"));
        assertThrows(IllegalArgumentException.class, () -> journal.prepare("https://other.example", AUTH));
    }

    private static final class SimulatedCrash extends RuntimeException {}
}
