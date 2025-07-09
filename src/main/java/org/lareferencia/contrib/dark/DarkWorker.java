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


import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.contrib.dark.client.*;
import org.lareferencia.contrib.dark.domain.DarkCredential;
import org.lareferencia.contrib.dark.domain.OAIIdentifierDark;
import org.lareferencia.contrib.dark.repositories.DarkCredentialRepository;
import org.lareferencia.contrib.dark.repositories.OAIIdentifierDarkRepository;
import org.lareferencia.contrib.dark.util.HttpUtils;
import org.lareferencia.contrib.dark.vo.DarkBusinessObject;
import org.lareferencia.core.metadata.IMetadataRecordStoreService;
import org.lareferencia.core.metadata.MetadataRecordStoreException;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.IPaginator;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DarkWorker extends BaseBatchWorker<OAIRecord, NetworkRunningContext> {

    public static final int DARK_PAGE_SIZE = 100;
    public static final String ARK_PREFIX = "ark:/";

    @Autowired
    private IMetadataRecordStoreService metadataStoreService;

    private static Logger logger = LogManager.getLogger(DarkWorker.class);

    private Long snapshotId;

    @Autowired
    private OAIIdentifierDarkRepository oaiIdentifierDarkRepository;

    @Autowired
    private DarkCredentialRepository darkCredentialRepository;

    private DarkCredential darkCredential;

    private List<DarkBusinessObject> recordsForRegistration = new ArrayList<>();
    private List<DarkBusinessObject> recordsForUrlUpdate = new ArrayList<>();

    @Setter
    @Getter
    @Value("${dark.minter.url:http://minter.dark-pid.net/}")
    private String minterURL;


    public DarkWorker() {
        super();
    }

    @Override
    public void preRun() {

        // busca el lgk
        snapshotId = metadataStoreService.findLastHarvestingSnapshot(runningContext.getNetwork());
        darkCredential = darkCredentialRepository.findByNetwork(runningContext.getNetwork().getId());
        recordsForRegistration = new ArrayList<>();
        recordsForUrlUpdate = new ArrayList<>();

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
        recordsForRegistration.clear();
        recordsForUrlUpdate.clear();
    }


    @Override
    public void processItem(OAIRecord oaiRecord) {

        try {
            DarkBusinessObject darkBusinessObject =
                    new DarkBusinessObject(oaiRecord, metadataStoreService, oaiIdentifierDarkRepository);

            darkBusinessObject.normalizeMetadataAndTrackingIfNeeded();

            switch (darkBusinessObject.getSituation()) {
                case NEED_TO_REGISTER:
                    recordsForRegistration.add(darkBusinessObject);
                    break;
                case NEED_TO_UPDATE_URL:
                    recordsForUrlUpdate.add(darkBusinessObject);
                    break;
            }

        } catch (Exception e) {
            logger.error(e);
            throw new RuntimeException("An error has ocurried during the DarkStep", e);
        }

    }


    @SneakyThrows
    @Override
    public void postPage() {

        if (!recordsForRegistration.isEmpty()) {
            sendNewItemsToDark();
        }

        if (!recordsForUrlUpdate.isEmpty()) {
            sendTrackedItemsToUpdateUrl();
        }

    }

    private void sendTrackedItemsToUpdateUrl() {
        URLUpdateResponse urlUpdateResponse = HttpUtils.sendMessageToHyperDrive(
                minterURL,
                new URLAssociationRequest(recordsForRegistration, darkCredential.getPrivateKey()),
                URLUpdateResponse.class);

        recordsForUrlUpdate.stream()
                .filter(darkBusinessObject -> urlUpdateResponse.getUpdatedPidsRaw().contains(darkBusinessObject.getDarkIdFromTracking()))
                .forEach(oaiRecordToDarkWrapper -> {
                    OAIIdentifierDark oaiIdentifierDark = oaiRecordToDarkWrapper.getDarkOptional().get();
                    oaiIdentifierDark.setItemUrl(oaiRecordToDarkWrapper.getItemUrlFromCollectedMetadata());
                    oaiIdentifierDark.setLastmodified(LocalDateTime.now());
                    oaiIdentifierDarkRepository.save(oaiIdentifierDark);
                });
    }

    private void sendNewItemsToDark() {
        URLAssociationResponse urlAssociationResponse =
                HttpUtils.sendMessageToHyperDrive(minterURL,
                        new URLAssociationRequest(recordsForRegistration, darkCredential.getPrivateKey()),
                        URLAssociationResponse.class);

        urlAssociationResponse.getIngested_pids().forEach(ingestedPid -> {

            DarkBusinessObject oaiRecordToDarkWrapper = recordsForRegistration.stream().filter(currentRecord ->
                    currentRecord.getOaiRecordMetadata().getIdentifier().equals(ingestedPid.getOai_id())).findFirst().get();

            String ark = ARK_PREFIX + ingestedPid.getArk();
            oaiRecordToDarkWrapper.setDarkId(ark);
            metadataStoreService.updatePublishedMetadata(oaiRecordToDarkWrapper.getOaiRecord(), oaiRecordToDarkWrapper.getOaiRecordMetadata());

            oaiIdentifierDarkRepository.save(new OAIIdentifierDark(ark, ingestedPid.getOai_id(), oaiRecordToDarkWrapper.getItemUrlFromCollectedMetadata()));
        });
    }


    @Override
    public void postRun() {


    }


}
