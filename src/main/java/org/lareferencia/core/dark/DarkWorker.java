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

import java.text.NumberFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.backend.domain.NetworkSnapshot;
import org.lareferencia.backend.domain.OAIBitstream;
import org.lareferencia.backend.domain.OAIBitstreamId;
import org.lareferencia.backend.repositories.jpa.OAIBitstreamRepository;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.core.metadata.IMetadataRecordStoreService;
import org.lareferencia.core.metadata.MetadataRecordStoreException;
import org.lareferencia.core.metadata.OAIMetadataBitstream;
import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.lareferencia.core.metadata.OAIRecordMetadataParseException;
import org.lareferencia.core.worker.BaseBatchWorker;
import org.lareferencia.core.worker.NetworkRunningContext;
import org.springframework.beans.factory.annotation.Autowired;

public class DarkWorker extends BaseBatchWorker<OAIRecord, NetworkRunningContext> {

	@Autowired
	private IMetadataRecordStoreService metadataStoreService;

	private static Logger logger = LogManager.getLogger(DarkWorker.class);
	
	private Long snapshotId;

	NumberFormat percentajeFormat = NumberFormat.getPercentInstance();

	@Autowired
	private OAIBitstreamRepository bitstreamRepository;

	public DarkWorker() {
		super();

	}

	@Override
	public void preRun() {

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

		logger.debug("Getting dARK for: " + record.getId());
		
	}

	@Override
	public void postPage() {

	}

	@Override
	public void postRun() {

	}

}
