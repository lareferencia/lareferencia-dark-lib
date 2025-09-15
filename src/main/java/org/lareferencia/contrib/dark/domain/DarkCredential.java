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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.persistence.Id;


/**
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class DarkCredential {

	@Id
	@Column(nullable = false, name = "network_id")
	private Long networkId;

	protected Long naan = null;

	@Column(nullable = false)
	private String privateKey;

	public DarkCredential(Long naan, String privateKey) {
		this.naan = naan;
		this.privateKey = privateKey;
	}


	public DarkCredential(Long naan, String privateKey, Long networkId) {
		this.naan = naan;
		this.privateKey = privateKey;
		this.networkId = networkId;
	}

	public String toString() {
		return  "naan=" + naan +", privateKey='" + privateKey + '\'' +", networkId=" + networkId;
	}	


}
