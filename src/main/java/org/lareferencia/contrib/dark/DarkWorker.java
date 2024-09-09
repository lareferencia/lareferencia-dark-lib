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

package org.lareferencia.contrib.dark;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.contrib.dark.contract.DarkBlockChainService;
import org.lareferencia.contrib.dark.domain.DarkCredential;
import org.lareferencia.contrib.dark.domain.OAIIdentifierDark;
import org.lareferencia.contrib.dark.repositories.DarkCredentialRepository;
import org.lareferencia.contrib.dark.repositories.OAIIdentifierDarkRepository;
import org.lareferencia.contrib.dark.vo.DarkId;
import org.lareferencia.contrib.dark.vo.DarkRecord;
import org.lareferencia.core.metadata.IMetadataRecordStoreService;
import org.lareferencia.core.metadata.MetadataRecordStoreException;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.IPaginator;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.lareferencia.core.worker.WorkerRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class DarkWorker extends BaseBatchWorker<OAIRecord, NetworkRunningContext> {

    public static final int DARK_PAGE_SIZE = 100;
    public static final String DC_IDENTIFIER_DARK = "dc.identifier.dark"; // TODO: make it configurable
    public static final String EMPTY = "";

    @Autowired
    private IMetadataRecordStoreService metadataStoreService;

    private static Logger logger = LogManager.getLogger(DarkWorker.class);

    private Long snapshotId;

    @Autowired
    private OAIIdentifierDarkRepository oaiIdentifierDarkRepository;

    @Autowired
    private DarkBlockChainService darkBlockChainService;

    @Autowired
    private DarkCredentialRepository darkCredentialRepository;

    final Queue<DarkId> availableDarkPids = new ArrayBlockingQueue<>(10000);

    private DarkCredential darkCredential;

    public DarkWorker() {
        super();

    }

    @Override
    public void preRun() {

        // busca el lgk
        snapshotId = metadataStoreService.findLastHarvestingSnapshot(runningContext.getNetwork());
        darkCredential = darkCredentialRepository.findByNetwork(runningContext.getNetwork().getId());

        if (snapshotId != null) { // solo si existe un lgk

            logger.debug("dARK PID processing: " + snapshotId);
            // establece una paginator para recorrer los registros que sean validos
            IPaginator<OAIRecord> validRecordsPaginator = metadataStoreService.getNotInvalidRecordsPaginator(snapshotId);
            validRecordsPaginator.setPageSize(DARK_PAGE_SIZE);

            this.setPaginator(validRecordsPaginator);


        } else {

            logger.warn("There are not harvested snapshots: " + runningContext.getNetwork().getAcronym());
            this.setPaginator(null);
            this.stop();
        }

    }

    @Override
    public void prePage() {
        List<DarkId> pidsInBulkMode = darkBlockChainService.getPidsInBulkMode(darkCredential.getPrivateKey());
        availableDarkPids.addAll(pidsInBulkMode);
    }


    @Override
    public void processItem(OAIRecord oaiRecord) {

        try {
            OAIRecordMetadata publishedMetadata = metadataStoreService.getPublishedMetadata(oaiRecord);

            boolean doesNotContainADarkId = EMPTY.equals(publishedMetadata.getFieldValue(DC_IDENTIFIER_DARK));

            if (doesNotContainADarkId) {
                logger.debug("Adding the OAI Identifier [{}] to be associated with an dArk Id", oaiRecord.getId());

                DarkRecord darkRecord = createDarkRecord(oaiRecord, publishedMetadata);

                publishedMetadata.addFieldOcurrence(DC_IDENTIFIER_DARK, darkRecord.getDarkId().getFormattedDarkId());
                metadataStoreService.updatePublishedMetadata(darkRecord.getOaiRecord(), publishedMetadata);

                darkBlockChainService.associateDarkPidWithUrl(darkRecord.getDarkId().getPidHashAsByteArray(), darkRecord.getUrl(), darkCredential.getPrivateKey());
                oaiIdentifierDarkRepository.save(new OAIIdentifierDark(darkRecord));
            }

        } catch (OAIRecordMetadataParseException | MetadataRecordStoreException | WorkerRuntimeException e) {
            throw new RuntimeException("An error has ocurried during the DarkStep", e);
        }

    }



    private DarkRecord createDarkRecord(OAIRecord oaiRecord, OAIRecordMetadata publishedMetadata) {
        DarkId darkId = availableDarkPids.poll();
        darkId.setFormattedDarkId(darkBlockChainService.formatPid(darkId.getPidHashAsByteArray(), darkCredential.getPrivateKey()));
        return new DarkRecord(oaiRecord, publishedMetadata, darkId);
    }

    @Override
    public void postPage() {

    }

    @Override
    public void postRun() {


    }



}
