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

package org.lareferencia.core.dark.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import lombok.Getter;
import lombok.Setter;
import org.lareferencia.core.dark.vo.DarkRecord;

import java.time.LocalDateTime;


/**
 */
@Entity
@Getter
@Setter
public class OAIIdentifierDark {

	@Id
	@Column(nullable = false)
	private String darkIdentifier;

	@Column(nullable = false)
	private String oaiIdentifier;

	@Column(nullable = false)
	private String rawDarkIdentifier;

	@Column(nullable = false)
	private Boolean metadata = false;

	@Column(nullable = false)
	private LocalDateTime datestamp;


	public OAIIdentifierDark(DarkRecord darkRecord, boolean metadata) {
		this.oaiIdentifier = darkRecord.getOaiIdentifier();
		this.darkIdentifier = darkRecord.getDarkId().getFormattedDarkId();
		this.datestamp = LocalDateTime.now();
		this.rawDarkIdentifier = darkRecord.getDarkId().getPidHashAsString();
		this.metadata = metadata;
	}

	public OAIIdentifierDark() {}

}
