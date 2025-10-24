package org.lareferencia.contrib.dark.services;

import org.lareferencia.contrib.dark.domain.OAIIdentifierDark;
import org.lareferencia.contrib.dark.repositories.OAIIdentifierDarkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DarkIdentifierStoreService {

    @Autowired
    private OAIIdentifierDarkRepository repository;



    public Optional<String> getDarkIdentifierByOAIIdentifier(String oaiIdentifier) {
        return repository.findByOaiIdentifier(oaiIdentifier)
                .map(OAIIdentifierDark::getDarkIdentifier);
    }

    public Optional<String> getOaiIdentifierByDark(String darkIdentifier) {
        return repository.findByDarkIdentifier(darkIdentifier)
                .map(OAIIdentifierDark::getOaiIdentifier);
    }
}