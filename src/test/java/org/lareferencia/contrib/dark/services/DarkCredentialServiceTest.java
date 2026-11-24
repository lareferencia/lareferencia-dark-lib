package org.lareferencia.contrib.dark.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lareferencia.contrib.dark.client.DarkMinterClientException;
import org.lareferencia.contrib.dark.domain.DarkRuntimeConfiguration;
import org.lareferencia.contrib.dark.repositories.DarkRuntimeConfigurationRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DarkCredentialServiceTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final DarkProperties properties = new DarkProperties();
    private final AtomicReference<DarkRuntimeConfiguration> saved = new AtomicReference<>();
    private DarkRuntimeConfigurationRepository repository;
    private PlatformTransactionManager transactions;
    private DarkRuntimeConfigurationService configuration;
    private DarkCredentialService credentials;

    @BeforeEach
    void setup() {
        repository = mock(DarkRuntimeConfigurationRepository.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(repository.findById(1L)).thenAnswer(call -> {
            if (saved.get() == null) return Optional.empty();
            var row = new DarkRuntimeConfiguration(); row.setConfiguration(saved.get().getConfiguration().deepCopy());
            return Optional.of(row);
        });
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            var row = (DarkRuntimeConfiguration) call.getArgument(0); saved.set(row); return row;
        });
        properties.setStoreBasePath(directory.toString());
        configuration = new DarkRuntimeConfigurationService(repository, mapper, properties, transactions, mock(DarkAttemptJournal.class));
        credentials = new DarkCredentialService(properties, configuration, new DarkKeyStorage());
        ObjectNode config = (ObjectNode) configuration.defaults();
        ObjectNode client = (ObjectNode) config.path("client");
        client.put("authorityId", "11111111-1111-4111-8111-111111111111");
        client.put("baseUrl", "https://minter.example.org");
        ((ObjectNode) client.path("authentication")).put("mode", "dark2");
        configuration.replace(config, "harvester-admin");
    }

    @Test
    void generatesBeforeAuthorityIsAssignedAndRequiresItForActivation() {
        ObjectNode config = (ObjectNode) configuration.get();
        ((ObjectNode) config.path("client")).put("authorityId", "");
        configuration.replace(config, "harvester-admin");
        var generated = credentials.generate(true, null, "harvester-admin");
        assertNotNull(generated.publicKey());
        assertThrows(IllegalArgumentException.class, () -> resume(generated));
        assertThrows(DarkMinterClientException.class, this::headers);
        ObjectNode assigned = (ObjectNode) configuration.get();
        ((ObjectNode) assigned.path("client")).put("authorityId", "assigned-authority");
        configuration.replace(assigned, "harvester-admin");
        var current = credentials.status();
        assertEquals(generated.credentialId(), current.credentialId());
        assertEquals(generated.keyId(), current.keyId());
        resume(current);
        assertEquals(6, headers().size());
    }

    @Test
    void derivesStorageFromHarvesterBasePathAndRejectsPathSelection() {
        String expected = directory.toAbsolutePath().normalize().resolve("keys").toString();
        assertEquals(expected, credentials.status().storageRoot());
        ObjectNode config = (ObjectNode) configuration.get();
        ((ObjectNode) config.path("client").path("authentication")).put("storageRoot", directory.resolve("other-keys").toString());
        assertThrows(IllegalArgumentException.class, () -> configuration.replace(config, "harvester-admin"));
        var generated = credentials.generate(true, null, "harvester-admin");
        assertTrue(java.nio.file.Files.isRegularFile(Path.of(expected).resolve(generated.credentialId()).resolve("private.pk8.pem")));
    }

    @Test
    void permitsHttpThroughoutConfigurationGenerationAndSigning() {
        ObjectNode config = (ObjectNode) configuration.get();
        ((ObjectNode) config.path("client")).put("baseUrl", "http://minter.example.org:8001");
        configuration.replace(config, "harvester-admin");
        var current = credentials.generate(true, null, "harvester-admin");
        resume(current);
        var signed = credentials.headers("POST", URI.create(current.baseUrl() + "/api/v1/arks/batch"), current.authorityId(), new byte[0]);
        assertEquals(6, signed.size());
    }

    @Test
    void rejectsInvalidBaseUrlsWhileAllowingHttpAndHttps() {
        for (String url : java.util.List.of("ftp://minter.example.org", "http://user:password@minter.example.org", "http://minter.example.org?query=1", "https://minter.example.org#fragment", "http:///missing-host")) {
            ObjectNode config = (ObjectNode) configuration.get();
            ((ObjectNode) config.path("client")).put("baseUrl", url);
            assertThrows(IllegalArgumentException.class, () -> configuration.replace(config, "harvester-admin"), url);
        }
    }

    @Test
    void singlePairLifecyclePersistsPauseAndRequiresCurrentFingerprint() {
        assertThrows(DarkMinterClientException.class, this::headers);
        var first = credentials.generate(true, null, "harvester-admin");
        assertEquals("pending_registration", first.registrationStatus());
        assertFalse(mapper.valueToTree(first).toString().contains("PRIVATE KEY"));
        assertThrows(DarkMinterClientException.class, this::headers);
        assertThrows(IllegalArgumentException.class, () -> credentials.resume(first.credentialId(), "wrong", first.authorityId(), first.baseUrl(), true, "harvester-admin"));
        resume(first);
        assertEquals(6, headers().size());
        var second = credentials.generate(true, first.credentialId(), "harvester-admin");
        assertNotEquals(first.credentialId(), second.credentialId());
        assertNotEquals(first.keyId(), second.keyId());
        assertThrows(IllegalArgumentException.class, () -> resume(first));
        assertThrows(DarkMinterClientException.class, this::headers);
        assertEquals("pending_registration", saved.get().getConfiguration().path("client").path("authentication").path("registrationStatus").asText());

        // Rebuild services, as after a process restart. The persisted pause remains effective.
        var restartedProperties = new DarkProperties(); restartedProperties.setStoreBasePath(properties.getStoreBasePath());
        var restartedConfiguration = new DarkRuntimeConfigurationService(repository, mapper, restartedProperties, transactions, mock(DarkAttemptJournal.class));
        restartedConfiguration.loadPersistedConfiguration();
        var restarted = new DarkCredentialService(restartedProperties, restartedConfiguration, new DarkKeyStorage());
        assertEquals(second.keyId(), restarted.status().keyId());
        assertThrows(DarkMinterClientException.class, () -> restarted.headers("POST", URI.create(second.baseUrl() + "/api/v1/arks/batch"), second.authorityId(), new byte[0]));
    }

    @Test
    void configurationCannotBypassLifecycleAndIdentityEditsPauseAgain() {
        var current = credentials.generate(true, null, "harvester-admin"); resume(current);
        ObjectNode config = (ObjectNode) configuration.get();
        ((ObjectNode) config.path("client").path("authentication")).put("registrationStatus", "pending_registration");
        assertThrows(IllegalArgumentException.class, () -> configuration.replace(config, "other-admin"));
        ObjectNode edited = (ObjectNode) configuration.get();
        ((ObjectNode) edited.path("client")).put("authorityId", "22222222-2222-4222-8222-222222222222");
        configuration.replace(edited, "other-admin");
        assertEquals("pending_registration", credentials.status().registrationStatus());
        assertThrows(IllegalArgumentException.class, () -> resume(current));
        assertThrows(DarkMinterClientException.class, this::headers);
        ObjectNode legacy = (ObjectNode) configuration.get();
        ((ObjectNode) legacy.path("client").path("authentication")).put("mode", "legacy");
        assertThrows(IllegalArgumentException.class, () -> configuration.replace(legacy, "other-admin"));
    }

    @Test
    void failedGenerationKeepsPauseAndOldReference() {
        var current = credentials.generate(true, null, "harvester-admin"); resume(current);
        DarkKeyStorage failing = mock(DarkKeyStorage.class);
        when(failing.generate(anyString(), anyString())).thenThrow(new IllegalStateException("disk unavailable"));
        var service = new DarkCredentialService(properties, configuration, failing);
        assertThrows(IllegalStateException.class, () -> service.generate(true, current.credentialId(), "harvester-admin"));
        assertEquals(current.credentialId(), credentials.status().credentialId());
        assertEquals("pending_registration", credentials.status().registrationStatus());
        assertThrows(DarkMinterClientException.class, this::headers);
    }

    private void resume(DarkCredentialService.Status value) {
        credentials.resume(value.credentialId(), value.keyId(), value.authorityId(), value.baseUrl(), true, "harvester-admin");
    }
    private java.util.Map<String, String> headers() {
        return credentials.headers("POST", URI.create("https://minter.example.org/api/v1/arks/batch"), properties.getAuthorityId(), new byte[0]);
    }
}
