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

package org.lareferencia.contrib.dark.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import org.lareferencia.contrib.dark.vo.DarkRecord;

import lombok.Getter;
import lombok.Setter;

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
	private LocalDateTime datestamp;

	@Column(nullable = true)
	private String itemUrl;

	@Column(nullable = true)
	private LocalDateTime lastmodified;


	public OAIIdentifierDark(String darkIdentifier, String oaiIdentifier, String itemUrl) {
		this.darkIdentifier = darkIdentifier;
		this.oaiIdentifier = oaiIdentifier;
		this.datestamp = LocalDateTime.now();
		this.lastmodified = LocalDateTime.now();
		this.itemUrl = itemUrl;
	}

	public OAIIdentifierDark() {}

}
