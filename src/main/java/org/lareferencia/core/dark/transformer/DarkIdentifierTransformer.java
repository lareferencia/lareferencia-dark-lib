
/*
 *   Copyright (c) 2013-2022. LA Referencia / Red CLARA and others
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   This file is part of LA Referencia software platform LRHarvester v4.x
 *   For any further information please contact Lautaro Matas <lmatas@gmail.com>
 */

package org.lareferencia.core.dark.transformer;

import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.core.dark.domain.OAIIdentifierDark;
import org.lareferencia.core.dark.repositories.OAIIdentifierDarkRepository;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.validation.AbstractTransformerRule;
import org.lareferencia.core.validation.ValidationException;

import java.util.List;
import java.util.Optional;

public class DarkIdentifierTransformer extends AbstractTransformerRule {

    private static final Logger LOG = LogManager.getLogger(DarkIdentifierTransformer.class);

    @Setter
    String targetFieldName;

    public DarkIdentifierTransformer() {
    }

    @Override
    public boolean transform(OAIRecord record, OAIRecordMetadata metadata) throws ValidationException {


        OAIIdentifierDarkRepository oaiIdentifierDarkRepository = getApplicationContext().getBean(OAIIdentifierDarkRepository.class);

        Optional<OAIIdentifierDark> darkAssociatedWithThisItemOptional =
                oaiIdentifierDarkRepository.findByOaiIdentifier(record.getIdentifier());

        if (darkAssociatedWithThisItemOptional.isPresent()) {

            OAIIdentifierDark oaiIdentifierDark = darkAssociatedWithThisItemOptional.get();

            boolean doesThisDarkIdAlreadyExistsInMetadata = doesThisDarkIdAlreadyExistsInMetadata(metadata, oaiIdentifierDark);
            if (!doesThisDarkIdAlreadyExistsInMetadata) {
                metadata.addFieldOcurrence(targetFieldName, oaiIdentifierDark.getDarkIdentifier());
                return true;
            }

        } else {
            LOG.debug("There's no DarkId associated for the the OAI Identifier [{}]", record.getIdentifier());
        }

        return false;
    }

    private boolean doesThisDarkIdAlreadyExistsInMetadata(OAIRecordMetadata metadata, OAIIdentifierDark oaiIdentifierDark) {
        List<String> fieldOcurrences = metadata.getFieldOcurrences(targetFieldName);

        if (fieldOcurrences != null && !fieldOcurrences.isEmpty()) {

            return fieldOcurrences.stream()
                    .filter(recordIdentifier -> recordIdentifier.equals(oaiIdentifierDark.getDarkIdentifier()))
                    .findFirst().get() != null;

        }
        return false;
    }
}
