package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lareferencia.core.domain.Network;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("DarkNetworkSettingsResolver tests")
class DarkNetworkSettingsResolverTest {

    private final DarkNetworkSettingsResolver resolver = new DarkNetworkSettingsResolver();

    @Test
    @DisplayName("Resolve ark_naan from network attributes")
    void resolvesArkNaan() {
        Network network = networkWithAttributes(Map.of("ark_naan", "12345"));

        assertEquals("12345", resolver.resolveArkNaan(network));
    }

    @Test
    @DisplayName("Trim ark_naan values from network attributes")
    void trimsArkNaan() {
        Network network = networkWithAttributes(Map.of("ark_naan", " 12345 "));

        assertEquals("12345", resolver.resolveArkNaan(network));
    }

    @Test
    @DisplayName("Fail when ark_naan is missing")
    void failsWhenArkNaanMissing() {
        Network network = networkWithAttributes(Map.of("repository_id", "repo-1"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> resolver.resolveArkNaan(network));
        assertEquals("network.attributes.ark_naan must be configured for network TEST(id:1)", error.getMessage());
    }

    @Test
    @DisplayName("Fail when ark_naan is blank")
    void failsWhenArkNaanBlank() {
        Network network = networkWithAttributes(Map.of("ark_naan", " "));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> resolver.resolveArkNaan(network));
        assertEquals("network.attributes.ark_naan must be configured for network TEST(id:1)", error.getMessage());
    }

    private Network networkWithAttributes(Map<String, Object> attributes) {
        Network network = new Network();
        network.setAcronym("TEST");
        network.setAttributes(attributes);
        ReflectionTestUtils.setField(network, "id", 1L);
        return network;
    }
}
