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

package org.lareferencia.core.dark;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.backend.repositories.jpa.OAIBitstreamRepository;
import org.lareferencia.backend.repositories.jpa.TransformerRepository;
import org.lareferencia.core.dark.contract.DarkPidVo;
import org.lareferencia.core.dark.contract.DarkService;
import org.lareferencia.core.dark.domain.OAIIdentifierDark;
import org.lareferencia.core.dark.repositories.OAIIdentifierDarkRepository;
import org.lareferencia.core.metadata.IMetadataRecordStoreService;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

public class DarkWorker extends BaseBatchWorker<OAIRecord, NetworkRunningContext> {

    public static final int NUMBER_OF_DARKPIDS_IN = 100;

    @Autowired
    private IMetadataRecordStoreService metadataStoreService;

    private static Logger logger = LogManager.getLogger(DarkWorker.class);

    private Long snapshotId;

    @Autowired
    private OAIBitstreamRepository bitstreamRepository;

    @Autowired
    private OAIIdentifierDarkRepository oaiIdentifierDarkRepository;

    @Autowired
    private DarkService darkService;

    private Set<String> oaiIdentifiersWithoutDarkId;

    public DarkWorker() {
        super();

    }

    @Override
    public void preRun() {

        oaiIdentifiersWithoutDarkId = new HashSet<>();
        // busca el lgk
        snapshotId = metadataStoreService.findLastGoodKnownSnapshot(runningContext.getNetwork());

        if (snapshotId != null) { // solo si existe un lgk

            logger.debug("dARK PID processing: " + snapshotId);
            // establece una paginator para recorrer los registros que sean validos
            this.setPaginator(metadataStoreService.getValidRecordsPaginator(snapshotId));


        } else {

            logger.warn("No hay LGKSnapshot de la red: " + runningContext.getNetwork().getAcronym());
            this.setPaginator(null);
            this.stop();
        }

    }

    @Override
    public void prePage() {
    }

    @Override
    public void processItem(OAIRecord record) {

        oaiIdentifierDarkRepository.findByOaiIdentifier(record.getIdentifier())
                .ifPresent(oaiIdentifierDark -> {
                    logger.debug("Adding the OAI Identificer [{}] to be associated with an dArk Id", record.getId());
                    oaiIdentifiersWithoutDarkId.add(oaiIdentifierDark.getOaiIdentifier());
                });

    }

    @Override
    public void postPage() {

    }

    @Override
    public void postRun() {

        boolean hasOaiWithoutDarkIdAssociated = oaiIdentifiersWithoutDarkId != null && !oaiIdentifiersWithoutDarkId.isEmpty();
        if (hasOaiWithoutDarkIdAssociated) {
            persistAssociationBetweenDarkIdAndOaiIdentifier();
        }

    }

    private void persistAssociationBetweenDarkIdAndOaiIdentifier() {
        final Queue<DarkPidVo> availableDarkPids = new ArrayBlockingQueue<>(10000);
        int amountOfTimesToRequestBulkPids = (int) Math.ceil((double) oaiIdentifiersWithoutDarkId.size() / NUMBER_OF_DARKPIDS_IN);

        while (amountOfTimesToRequestBulkPids-- != 0) {
            logger.debug("Step [{}] - Requesting dArk pid in bulk mode", amountOfTimesToRequestBulkPids);
            availableDarkPids.addAll(darkService.getPidsInBulkMode());
        }

        oaiIdentifiersWithoutDarkId.forEach(oaiIdentificer ->
                oaiIdentifierDarkRepository.save(new OAIIdentifierDark(oaiIdentificer, availableDarkPids.poll().pidHash)));
    }

}
