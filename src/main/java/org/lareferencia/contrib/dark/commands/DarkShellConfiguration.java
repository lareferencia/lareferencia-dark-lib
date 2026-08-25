package org.lareferencia.contrib.dark.commands;

import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal dARK configuration for the administrative shell.
 *
 * <p>It intentionally excludes the stage and reconciliation services, whose
 * dependencies belong to the harvester application rather than the shell.</p>
 */
@Configuration
@EntityScan(basePackageClasses = DarkTrackingRecord.class)
@EnableJpaRepositories(basePackageClasses = DarkTrackingRepository.class)
@ComponentScan(basePackageClasses = DarkImportCommands.class)
public class DarkShellConfiguration {
}
