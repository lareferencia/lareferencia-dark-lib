package org.lareferencia.contrib.dark.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lareferencia.contrib.dark.domain.*;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.*;
import org.lareferencia.contrib.dark.worker.DarkStageWorker;
import org.lareferencia.core.domain.Network;
import org.lareferencia.core.metadata.IMetadataStore;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.*;

class DarkAttemptRecoveryTest {
    private static final String OAI = "oai:test:1";
    private static final String ARK = "ark:12345/abc";
    private static final String RESERVED = """
            {"results":[{"client_item_id":"oai:test:1","ark":"ark:12345/abc","state":"R"}]}
            """;
    private static final String PUBLISHED = """
            {"ark":"ark:12345/abc","state":"P"}
            """;

    @EnableJpaRepositories(basePackageClasses = DarkTrackingRepository.class)
    @EnableTransactionManagement
    static class Persistence {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setPackagesToScan("org.lareferencia.contrib.dark.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",
                    System.getProperty("dark.test.jdbc-url") == null ? "create-drop" : "validate"));
            return factory;
        }

        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    private DarkTrackingRepository rows;
    private JpaTransactionManager transactions;
    private DarkAttemptTracking tracking;
    private DarkProperties properties;
    private DataSource ds;
    private String schema;
    private JdbcTemplate postgres;

    @BeforeEach void openDatabase() throws Exception {
        String url = System.getProperty("dark.test.jdbc-url");
        if (url == null) {
            ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        } else {
            schema = "dark_test_" + UUID.randomUUID().toString().replace("-", "");
            postgres = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "dark-test"));
            postgres.execute("create schema " + schema);
            ds = new DriverManagerDataSource(url + "?currentSchema=" + schema, "postgres", "dark-test");
            // Exercise the released PostgreSQL tracking schema with Hibernate in validation-only mode.
            var migrations = Path.of("../lareferencia-shell/src/main/resources/db/migration");
            for (String file : List.of("V5.0.0.4__Dark_Tracking_Rework.sql", "V5.0.0.5__Dark_Reconcile_Index.sql",
                    "V5.0.0.6__Dark_Stage_Payload_Hash.sql", "V5.0.0.9__Dark_runtime_configuration.sql",
                    "V5.0.0.10__Dark_Tracking_Provenance.sql")) {
                String sql = Files.readString(migrations.resolve(file)).replace("public.", schema + ".");
                var resource = new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8));
                new ResourceDatabasePopulator(resource).execute(ds);
            }
        }
        context.registerBean(DataSource.class, () -> ds);
        context.register(Persistence.class, DarkAttemptTracking.class, DarkErrorCodec.class);
        context.refresh();
        rows = context.getBean(DarkTrackingRepository.class);
        if (postgres == null) {
            // Advisory locks are PostgreSQL-specific. Keep real JPA transactions on both databases.
            rows = mock(DarkTrackingRepository.class, org.mockito.AdditionalAnswers.delegatesTo(rows));
            doReturn(1).when(rows).lockWorkerPage(anyString());
        }
        transactions = context.getBean(JpaTransactionManager.class);
        tracking = context.getBean(DarkAttemptTracking.class);
        properties = new DarkProperties();
        properties.setAuthorityId("test");
        properties.getMinter().setBaseUrl("https://minter.example");
    }

    @AfterEach void closeDatabase() {
        context.close();
        if (postgres != null) postgres.execute("drop schema " + schema + " cascade");
    }

    @Test void interruptedReservationRecoversWithOriginalOaiAndAcceptedStageSurvivesPageRollback() throws Exception {
        var http = mock(HttpClient.class);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            assertReservationId(call.getArgument(0));
            assertPendingOnAnotherConnection("RESERVE");
            throw new SimulatedCrash();
        });
        assertThrows(SimulatedCrash.class, () -> runPage(http, false));
        assertNull(row().getArk());
        var restarted = mock(HttpClient.class);
        var reservation = response(RESERVED);
        var stage = response(PUBLISHED);
        when(restarted.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            if ("POST".equals(request.method())) {
                assertReservationId(request);
                assertPendingOnAnotherConnection("RESERVE");
                return reservation;
            }
            assertPendingOnAnotherConnection("STAGE");
            assertEquals(ARK, row().getArk());
            return stage;
        });
        assertThrows(SimulatedCrash.class, () -> runPage(restarted, true));
        assertEquals(1, rows.count());
        assertEquals(DarkTrackingState.PUBLISHED, row().getState());
        assertNotNull(row().getStagePayloadHash());
        assertNull(row().getLastError());
        var next = mock(HttpClient.class);
        runPage(next, false);
        verifyNoInteractions(next);
    }

    @Test void interruptedStagePreservesOldHashAndRestartSkipsAutomaticResend() throws Exception {
        var previous = new DarkTrackingRecord();
        previous.setId(DarkTrackingRecordId.of("12345", OAI));
        previous.setArk(ARK);
        previous.setStagePayloadHash("previous");
        previous.setState(DarkTrackingState.PUBLISHED);
        rows.save(previous);
        var http = mock(HttpClient.class);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            assertPendingOnAnotherConnection("STAGE");
            throw new SimulatedCrash();
        });
        assertThrows(SimulatedCrash.class, () -> runPage(http, false));
        var restarted = mock(HttpClient.class);
        runPage(restarted, false);
        verifyNoInteractions(restarted);
        assertEquals("previous", row().getStagePayloadHash());
        assertTrue(new DarkErrorCodec().hasUnknownOutcome(row().getLastError()));
        assertEquals(1, rows.count());
    }

    private void assertReservationId(HttpRequest request) throws Exception {
        var json = new ObjectMapper().readTree(DarkMinterClientTest.requestBody(request));
        assertEquals(OAI, json.at("/items/0/client_item_id").asText());
    }

    private void assertPendingOnAnotherConnection(String phase) throws Exception {
        try (var connection = ds.getConnection(); var query = connection.prepareStatement(
                "select last_error from dark_tracking_record where ark_naan=? and oai_id=?")) {
            query.setString(1, "12345");
            query.setString(2, OAI);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                var marker = new ObjectMapper().readTree(result.getString(1));
                assertEquals(DarkMinterClientException.MODIFICATION_OUTCOME_UNKNOWN, marker.path("code").asText());
                assertEquals(phase, marker.path("phase").asText());
            }
        }
    }

    private DarkTrackingRecord row() {
        return rows.findById(DarkTrackingRecordId.of("12345", OAI)).orElseThrow();
    }

    private HttpResponse<String> response(String body) {
        HttpResponse<String> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn(body);
        return reply;
    }

    private void runPage(HttpClient http, boolean crashAfterReply) throws Exception {
        // Each pass creates a new worker and client, as after a process restart.
        var worker = new DarkStageWorker();
        invokeMethod(worker, "configureAttemptTransactions", transactions);
        setField(worker, "darkTrackingRepository", rows);
        setField(worker, "darkMinterClient", new DarkMinterClient(http, new ObjectMapper(), properties, null, tracking));
        setField(worker, "darkProperties", properties);
        setField(worker, "runningContext", new NetworkRunningContext(new Network()));
        setField(worker, "snapshotMetadata", mock(SnapshotMetadata.class));
        setField(worker, "currentArkNaan", "12345");
        setField(worker, "currentSourceMetadataSchema", "xoai");
        setField(worker, "currentTargetMetadataSchema", "xoai");
        var metadata = mock(IMetadataStore.class);
        when(metadata.getMetadata(any(), any())).thenReturn("<metadata/>");
        setField(worker, "metadataStore", metadata);
        var transform = mock(DarkOriginalMetadataTransformerService.class);
        when(transform.transformForMinter(any(), any(), any(), any(), any())).thenReturn("<metadata/>");
        setField(worker, "darkOriginalMetadataTransformerService", transform);
        var urls = mock(UrlExtractionService.class);
        when(urls.extractBestUrl(any())).thenReturn("https://example.org/item");
        setField(worker, "urlExtractionService", urls);
        setField(worker, "level1MetadataService", mock(DarkLevel1MetadataService.class));
        var item = OAIRecord.create(OAI, null, "source-hash", false);
        new TransactionTemplate(transactions).executeWithoutResult(page -> {
            worker.prePage();
            worker.processItem(item);
            worker.postPage();
            if (crashAfterReply) throw new SimulatedCrash();
        });
    }

    private static class SimulatedCrash extends Error {}
}
