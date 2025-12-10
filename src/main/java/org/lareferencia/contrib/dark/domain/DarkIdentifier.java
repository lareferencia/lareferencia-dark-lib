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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity that maps an OAI identifier to its DARK persistent identifier (ARK).
 * This is the local tracking record for registered PIDs.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class DarkIdentifier {

    /** The DARK/ARK identifier (e.g., "ark:/99999/abc123") */
    @Id
    @Column(nullable = false)
    private String darkId;

    /** The OAI identifier from the source repository */
    @Column(nullable = false)
    private String oaiId;

    /** The resolved URL for this identifier */
    @Column
    private String url;

    /** When this identifier was first registered */
    @Column(nullable = false)
    private LocalDateTime created;

    /** When this identifier was last updated */
    @Column
    private LocalDateTime updated;

    public DarkIdentifier(String darkId, String oaiId, String url) {
        this.darkId = darkId;
        this.oaiId = oaiId;
        this.url = url;
        this.created = LocalDateTime.now();
        this.updated = LocalDateTime.now();
    }
}
