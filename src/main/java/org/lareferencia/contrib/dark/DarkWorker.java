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
import org.lareferencia.contrib.dark.client.URLAssociationRequest;
import org.lareferencia.contrib.dark.client.URLAssociationResponse;
import org.lareferencia.contrib.dark.domain.DarkCredential;
import org.lareferencia.contrib.dark.domain.OAIIdentifierDark;
import org.lareferencia.contrib.dark.repositories.DarkCredentialRepository;
import org.lareferencia.contrib.dark.repositories.OAIIdentifierDarkRepository;
import org.lareferencia.contrib.dark.vo.OaiRecordWrapper;
import org.lareferencia.core.metadata.IMetadataRecordStoreService;
import org.lareferencia.core.metadata.MetadataRecordStoreException;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.IPaginator;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DarkWorker extends BaseBatchWorker<OAIRecord, NetworkRunningContext> {

    public static final int DARK_PAGE_SIZE = 100;
    public static final String DC_IDENTIFIER_DARK = "dc.identifier.dark";
    public static final String EMPTY = "";
    public static final int STATUS_SUCCESS = 200;

    @Autowired
    private IMetadataRecordStoreService metadataStoreService;

    private static Logger logger = LogManager.getLogger(DarkWorker.class);

    private Long snapshotId;

    @Autowired
    private OAIIdentifierDarkRepository oaiIdentifierDarkRepository;

    @Autowired
    private DarkCredentialRepository darkCredentialRepository;

    private DarkCredential darkCredential;

    private List<OaiRecordWrapper> recordsToProcess = new ArrayList<>();

    @Setter
    @Getter
    @Value("${hyperdrive.url}")
    private String hyperdriveUrl;


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
        recordsToProcess.clear();
    }


    @Override
    public void processItem(OAIRecord oaiRecord) {

        try {
            OAIRecordMetadata publishedMetadata = metadataStoreService.getPublishedMetadata(oaiRecord);
            Optional<OAIIdentifierDark> oaiIdentifierDarkOptional = oaiIdentifierDarkRepository.findByOaiIdentifier(oaiRecord.getIdentifier());

            boolean doesNotContainADarkId = EMPTY.equals(publishedMetadata.getFieldValue(DC_IDENTIFIER_DARK));

            if(oaiIdentifierDarkOptional.isPresent()) {

                if(doesNotContainADarkId) {
                    publishedMetadata.addFieldOcurrence(DC_IDENTIFIER_DARK, oaiIdentifierDarkOptional.get().getDarkIdentifier());
                    metadataStoreService.updatePublishedMetadata(oaiRecord, publishedMetadata);
                }

            } else  {

                if(doesNotContainADarkId) {
                    // Register this item to be processed
                    recordsToProcess.add(new OaiRecordWrapper(oaiRecord, publishedMetadata));
                } else {
                    // The metadata has dc.identifier.dark but is not in "OAIIdentifierDark", then insert it
                    oaiIdentifierDarkRepository.save(new OAIIdentifierDark(publishedMetadata.getFieldValue(DC_IDENTIFIER_DARK), oaiRecord.getIdentifier()));
                }

            }


        } catch (OAIRecordMetadataParseException | MetadataRecordStoreException e) {
            logger.error(e);
            throw new RuntimeException("An error has ocurried during the DarkStep", e);
        }

    }


    @SneakyThrows
    @Override
    public void postPage() {

        if (!recordsToProcess.isEmpty()) {
            HttpRequest request = HttpRequest.newBuilder(new URI(hyperdriveUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(new URLAssociationRequest(recordsToProcess, darkCredential.getPrivateKey()).asJson()))
                    .build();

            HttpResponse<String> httpResponse = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = httpResponse.body();
            logger.debug("Got the status [{}] and the following message from hyperdrive [{}]", httpResponse.statusCode(), responseBody);

            if (httpResponse.statusCode() == STATUS_SUCCESS) {
                URLAssociationResponse urlAssociationResponse = URLAssociationResponse.fromString(responseBody);
                urlAssociationResponse.getIngested_pids().forEach(ingestedPid -> {

                    OaiRecordWrapper oaiRecordWrapper = recordsToProcess.stream().filter(currentRecord ->
                            currentRecord.getOaiRecordMetadata().getIdentifier().equals(ingestedPid.getOai_id())).findFirst().get();

                    String ark = "ark:/" + ingestedPid.getArk();
                    oaiRecordWrapper.getOaiRecordMetadata().addFieldOcurrence(DC_IDENTIFIER_DARK, ark);
                    metadataStoreService.updatePublishedMetadata(oaiRecordWrapper.getOaiRecord(), oaiRecordWrapper.getOaiRecordMetadata());

                    oaiIdentifierDarkRepository.save(new OAIIdentifierDark(ark, ingestedPid.getOai_id()));
                });
            }
        }


    }


    @Override
    public void postRun() {


    }


}
