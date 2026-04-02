package org.lareferencia.contrib.dark.worker;

import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.core.worker.IPaginator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;

public class DarkTrackingPaginator implements IPaginator<DarkTrackingRecord> {

    private final DarkTrackingRepository repository;
    private final String arkNaan;
    private final Collection<DarkTrackingState> states;
    private int pageSize = 100;
    private int currentPage = 0;
    private int totalPages = 0;
    private boolean initialized = false;

    public DarkTrackingPaginator(DarkTrackingRepository repository, String arkNaan, Collection<DarkTrackingState> states) {
        this.repository = repository;
        this.arkNaan = arkNaan;
        this.states = states;
    }

    @Override
    public int getStartingPage() {
        return 1;
    }

    @Override
    public int getTotalPages() {
        ensureInitialized();
        return totalPages;
    }

    @Override
    public Page<DarkTrackingRecord> nextPage() {
        ensureInitialized();
        Page<DarkTrackingRecord> page = repository.findByIdArkNaanAndArkIsNotNullAndStateIn(
                arkNaan,
                states,
                PageRequest.of(currentPage, pageSize));
        currentPage++;
        return page;
    }

    @Override
    public void setPageSize(int size) {
        if (initialized) {
            throw new IllegalStateException("Cannot change page size after paginator initialization");
        }
        this.pageSize = size;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        long totalCount = repository.countByIdArkNaanAndArkIsNotNullAndStateIn(arkNaan, states);
        totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        initialized = true;
    }
}
