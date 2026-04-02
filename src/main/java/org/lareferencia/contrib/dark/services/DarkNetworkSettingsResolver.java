package org.lareferencia.contrib.dark.services;

import org.lareferencia.core.domain.Network;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DarkNetworkSettingsResolver {

    public static final String ARK_NAAN_ATTRIBUTE = "ark_naan";

    public String resolveArkNaan(Network network) {
        if (network == null) {
            throw new IllegalStateException("Network context is required to resolve the ARK NAAN");
        }

        return resolveArkNaan(network.getAttributes(), describeNetwork(network));
    }

    public String resolveArkNaan(Map<String, Object> attributes, String contextLabel) {
        if (attributes == null || !attributes.containsKey(ARK_NAAN_ATTRIBUTE)) {
            throw new IllegalStateException("network.attributes." + ARK_NAAN_ATTRIBUTE
                    + " must be configured for network " + contextLabel);
        }

        Object value = attributes.get(ARK_NAAN_ATTRIBUTE);
        String naan = value == null ? "" : String.valueOf(value).trim();
        if (naan.isBlank()) {
            throw new IllegalStateException("network.attributes." + ARK_NAAN_ATTRIBUTE
                    + " must be configured for network " + contextLabel);
        }

        return naan;
    }

    private String describeNetwork(Network network) {
        return network.getAcronym() + "(id:" + network.getId() + ")";
    }
}
