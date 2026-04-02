package org.lareferencia.contrib.dark.repositories;

import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecordId;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface DarkTrackingRepository extends JpaRepository<DarkTrackingRecord, DarkTrackingRecordId> {

    Optional<DarkTrackingRecord> findByArk(String ark);

    Optional<DarkTrackingRecord> findByIdArkNaanAndIdOaiId(String arkNaan, String oaiId);

    Page<DarkTrackingRecord> findByIdArkNaanAndArkIsNotNullAndStateIn(
            String arkNaan,
            Collection<DarkTrackingState> states,
            Pageable pageable);

    long countByIdArkNaanAndArkIsNotNullAndStateIn(String arkNaan, Collection<DarkTrackingState> states);
}
