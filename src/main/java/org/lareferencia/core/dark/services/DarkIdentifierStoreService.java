package org.lareferencia.core.dark.services;

import org.lareferencia.core.dark.domain.OAIIdentifierDark;
import org.lareferencia.core.dark.repositories.OAIIdentifierDarkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DarkIdentifierStoreService {

    @Autowired
    private OAIIdentifierDarkRepository repository;

    public OAIIdentifierDark addDarkIdentifier(String oaiIdentifier, String darkIdentifier) {
        OAIIdentifierDark newPair = new OAIIdentifierDark(oaiIdentifier, darkIdentifier);
        return repository.save(newPair);
    }

    public Optional<String> getDarkIdentifierByOAIIdentifier(String oaiIdentifier) {
        return repository.findByOaiIdentifier(oaiIdentifier)
                .map(OAIIdentifierDark::getDarkIdentifier);
    }

    public Optional<String> getOaiIdentifierByDark(String darkIdentifier) {
        return repository.findByDarkIdentifier(darkIdentifier)
                .map(OAIIdentifierDark::getOaiIdentifier);
    }
}