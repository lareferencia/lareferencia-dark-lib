
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
package org.lareferencia.contrib.dark.commands;

import java.util.Collection;
import java.util.logging.Logger;

import org.lareferencia.contrib.dark.domain.DarkCredential;
import org.lareferencia.contrib.dark.services.DarkCredentialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class DarkCommands {
	
	Logger logger = Logger.getLogger(DarkCommands.class.getName());

	@Autowired
	private DarkCredentialService darkCredentialService;

	@ShellMethod("Create a new Dark Credential")
	public String createDarkCredential(Long naan, String privateKey, Long networkId) {
		logger.info("Creating Dark Credential for NAAN: " + naan);
		try {
			darkCredentialService.createDarkCredential(naan, privateKey, networkId);
		} catch (Exception e) {
			return "Error creating Dark Credential: " + e.getMessage();
		}
		return "Dark Credential created successfully";
	}
	
	// list credentials
	@ShellMethod("List all Dark Credentials")
	public String listDarkCredentials() {
		logger.info("Listing all Dark Credentials");
		Collection<DarkCredential> credentials = darkCredentialService.listDarkCredentials();
		for (DarkCredential credential : credentials) {
			System.out.println(credential);
		}

		return "Dark Credentials listed successfully";
	}

	// delete credential
	@ShellMethod("Delete a Dark Credential by NAAN")
	public String deleteDarkCredential(Long naan) {
		logger.info("Deleting Dark Credential for NAAN: " + naan);
		darkCredentialService.deleteDarkCredential(naan);
		return "Dark Credential deleted successfully";
	}
		
}
