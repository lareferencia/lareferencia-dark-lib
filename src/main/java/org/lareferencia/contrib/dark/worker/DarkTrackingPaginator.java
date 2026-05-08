package org.lareferencia.contrib.dark.worker;

import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.core.worker.IPaginator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;

public class DarkTrackingPaginator implements IPaginator<DarkTrackingRecord> {

    private final DarkTrackingRepository repository;
    private final String arkNaan;
    private final Collection<DarkTrackingState> states;
    private int pageSize = 100;
    private int totalPages = 0;
    private long totalCount = 0;
    private String lastOaiId;
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
        Page<DarkTrackingRecord> page = fetchNextPage();
        if (!page.isEmpty()) {
            lastOaiId = page.getContent().get(page.getNumberOfElements() - 1).getOaiId();
        }
        return new PageImpl<>(page.getContent(), page.getPageable(), totalCount);
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
        totalCount = repository.countByIdArkNaanAndArkIsNotNullAndStateIn(arkNaan, states);
        totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        initialized = true;
    }

    private Page<DarkTrackingRecord> fetchNextPage() {
        if (lastOaiId == null) {
            return repository.findReconcilePage(
                    arkNaan,
                    states,
                    PageRequest.of(0, pageSize));
        }

        return repository.findReconcilePageAfter(
                arkNaan,
                states,
                lastOaiId,
                PageRequest.of(0, pageSize));
    }
}
