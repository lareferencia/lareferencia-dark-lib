package org.lareferencia.core.dark.services;

import org.lareferencia.core.dark.domain.DarkCredential;
import org.lareferencia.core.dark.repositories.DarkCredentialRepository;
import org.lareferencia.core.dark.repositories.OAIIdentifierDarkRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DarkCredentialService {

    @Autowired
    private DarkCredentialRepository darkCredentialRepository;

    @Autowired
    private OAIIdentifierDarkRepository oaiIdentifierDarkRepository;

    public DarkCredential createDarkCredential(Long naan, String privateKey) {
        // Aquí puedes añadir cualquier lógica de negocio antes de guardar
        return darkCredentialRepository.save( new DarkCredential(naan, privateKey) );
    }

    public String getPrivateKeyByNAAN(Long naan) {
        return darkCredentialRepository.findByNetwork(naan).getPrivateKey();
    }
}