package org.lareferencia.contrib.dark.services;

import java.util.Collection;

import org.lareferencia.backend.domain.Network;
import org.lareferencia.backend.repositories.jpa.NetworkRepository;
import org.lareferencia.contrib.dark.domain.DarkCredential;
import org.lareferencia.contrib.dark.repositories.DarkCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DarkCredentialService {

    @Autowired
    private DarkCredentialRepository darkCredentialRepository;

    @Autowired
    NetworkRepository networkRepository;

    public DarkCredential createDarkCredential(Long naan, String privateKey) {
        return darkCredentialRepository.save( new DarkCredential(naan, privateKey) );
    }

    public DarkCredential createDarkCredential(Long naan, String privateKey, Long networkId) throws Exception {

        Network network = networkRepository.findById(networkId).orElse(null);

        if (network == null) {
            throw new Exception("Network/Repository " + networkId +  " not found");
        } 

        return darkCredentialRepository.save( new DarkCredential(naan, privateKey, networkId) );
    }

    public String getPrivateKeyByNAAN(Long naan) {
        return darkCredentialRepository.findByNetwork(naan).getPrivateKey();
    }

    public Collection<DarkCredential> listDarkCredentials() {
        return darkCredentialRepository.findAll();
    }

    public void deleteDarkCredential(Long naan) {
        darkCredentialRepository.deleteById(naan);
    }


}