package org.lareferencia.contrib.dark.services;

import java.util.Collection;
import java.util.Optional;

import org.lareferencia.core.domain.Network;
import org.lareferencia.core.repository.jpa.NetworkRepository;
import org.lareferencia.contrib.dark.domain.DarkCredential;
import org.lareferencia.contrib.dark.repositories.DarkCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing DARK credentials.
 * Each network requires a credential (NAAN + private key) to interact with the DARK minter service.
 */
@Service
public class DarkCredentialService {

    @Autowired
    private DarkCredentialRepository darkCredentialRepository;

    @Autowired
    private NetworkRepository networkRepository;

    /**
     * Creates a new DARK credential for a network.
     *
     * @param naan       The Name Assigning Authority Number
     * @param privateKey The DNAM private key
     * @param networkId  The network ID to associate with
     * @return The created credential
     * @throws IllegalArgumentException if the network is not found
     */
    public DarkCredential createDarkCredential(Long naan, String privateKey, Long networkId) {
        Optional<Network> network = networkRepository.findById(networkId);
        if (network.isEmpty()) {
            throw new IllegalArgumentException("Network/Repository " + networkId + " not found");
        }
        return darkCredentialRepository.save(new DarkCredential(naan, privateKey, networkId));
    }

    /**
     * Lists all DARK credentials.
     */
    public Collection<DarkCredential> listDarkCredentials() {
        return darkCredentialRepository.findAll();
    }

    /**
     * Deletes a DARK credential by network ID.
     */
    public void deleteDarkCredential(Long networkId) {
        darkCredentialRepository.deleteById(networkId);
    }
}