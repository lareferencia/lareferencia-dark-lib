package org.lareferencia.contrib.dark.repositories;

import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecordId;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface DarkTrackingRepository extends JpaRepository<DarkTrackingRecord, DarkTrackingRecordId> {

    Optional<DarkTrackingRecord> findByArk(String ark);

    Optional<DarkTrackingRecord> findByIdArkNaanAndIdOaiId(String arkNaan, String oaiId);

    Page<DarkTrackingRecord> findByIdArkNaanAndArkIsNotNullAndStateIn(
            String arkNaan,
            Collection<DarkTrackingState> states,
            Pageable pageable);

    @Query("""
            select record
            from DarkTrackingRecord record
            where record.id.arkNaan = :arkNaan
              and record.ark is not null
              and record.state in :states
            order by record.id.oaiId asc
            """)
    Page<DarkTrackingRecord> findReconcilePage(
            @Param("arkNaan") String arkNaan,
            @Param("states") Collection<DarkTrackingState> states,
            Pageable pageable);

    @Query("""
            select record
            from DarkTrackingRecord record
            where record.id.arkNaan = :arkNaan
              and record.ark is not null
              and record.state in :states
              and record.id.oaiId > :afterOaiId
            order by record.id.oaiId asc
            """)
    Page<DarkTrackingRecord> findReconcilePageAfter(
            @Param("arkNaan") String arkNaan,
            @Param("states") Collection<DarkTrackingState> states,
            @Param("afterOaiId") String afterOaiId,
            Pageable pageable);

    long countByIdArkNaanAndArkIsNotNullAndStateIn(String arkNaan, Collection<DarkTrackingState> states);
}
