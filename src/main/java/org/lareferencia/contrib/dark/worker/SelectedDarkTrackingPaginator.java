package org.lareferencia.contrib.dark.worker;

import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.core.worker.IPaginator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.List;

/** Paginates only the explicit selection of a manual command (at most 100 IDs). */
public class SelectedDarkTrackingPaginator implements IPaginator<DarkTrackingRecord> {
    private final DarkTrackingRepository repository;
    private final String arkNaan;
    private final List<String> oaiIds;
    private final Collection<DarkTrackingState> states;
    private int pageSize = 100;
    private boolean consumed;
    private long totalCount;

    public SelectedDarkTrackingPaginator(DarkTrackingRepository repository, String arkNaan,
            Collection<String> oaiIds, Collection<DarkTrackingState> states) {
        this.repository = repository;
        this.arkNaan = arkNaan;
        this.oaiIds = List.copyOf(oaiIds);
        this.states = states;
    }

    @Override
    public int getStartingPage() { return 1; }

    @Override
    public int getTotalPages() { return oaiIds.isEmpty() ? 0 : 1; }

    @Override
    public Page<DarkTrackingRecord> nextPage() {
        if (consumed || oaiIds.isEmpty()) {
            return Page.empty(PageRequest.of(0, pageSize));
        }
        consumed = true;
        Page<DarkTrackingRecord> page = repository.findByIdArkNaanAndIdOaiIdInAndArkIsNotNullAndStateIn(
                arkNaan, oaiIds, states, PageRequest.of(0, pageSize));
        totalCount = page.getTotalElements();
        return page;
    }

    @Override
    public void setPageSize(int size) {
        if (consumed) throw new IllegalStateException("Cannot change page size after paginator consumption");
        this.pageSize = size;
    }
}
