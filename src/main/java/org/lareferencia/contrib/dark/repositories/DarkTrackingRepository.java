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
import java.time.LocalDateTime;

public interface DarkTrackingRepository extends JpaRepository<DarkTrackingRecord, DarkTrackingRecordId> {

    interface StateCount {
        DarkTrackingState getState();
        long getCount();
    }

    interface NaanCount {
        String getArkNaan();
        long getCount();
    }

    interface NaanStateCount {
        String getArkNaan();
        DarkTrackingState getState();
        long getCount();
    }

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

    @Query("select r from DarkTrackingRecord r where (:arkNaan is null or r.id.arkNaan = :arkNaan) "
            + "and (:state is null or r.state = :state) "
            + "and (:q = '' or lower(cast(coalesce(r.ark, '') as string)) like :qPattern "
            + "or lower(cast(r.id.oaiId as string)) like :qPattern) order by r.updatedAt desc")
    Page<DarkTrackingRecord> search(@Param("arkNaan") String arkNaan, @Param("state") DarkTrackingState state,
            @Param("q") String q, @Param("qPattern") String qPattern, Pageable pageable);

    @Query("select r.state as state, count(r) as count from DarkTrackingRecord r "
            + "where (:arkNaan is null or r.id.arkNaan = :arkNaan) group by r.state")
    Collection<StateCount> countByState(@Param("arkNaan") String arkNaan);

    @Query("select r.id.arkNaan as arkNaan, count(r) as count from DarkTrackingRecord r group by r.id.arkNaan order by r.id.arkNaan")
    Collection<NaanCount> countByNaan();

    @Query("select r.id.arkNaan as arkNaan, r.state as state, count(r) as count "
            + "from DarkTrackingRecord r group by r.id.arkNaan, r.state order by r.id.arkNaan, r.state")
    Collection<NaanStateCount> countByNaanAndState();
}
