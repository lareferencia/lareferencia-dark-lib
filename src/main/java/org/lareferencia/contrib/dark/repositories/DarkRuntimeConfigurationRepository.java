package org.lareferencia.contrib.dark.repositories;

import org.lareferencia.contrib.dark.domain.DarkRuntimeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DarkRuntimeConfigurationRepository extends JpaRepository<DarkRuntimeConfiguration, Long> { }
