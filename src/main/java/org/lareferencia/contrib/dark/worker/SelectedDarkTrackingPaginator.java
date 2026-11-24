package org.lareferencia.contrib.dark.worker;

import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.springframework.data.domain.PageImpl;
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
    private int pageSize = 100;
    private int nextPage;

    public SelectedDarkTrackingPaginator(DarkTrackingRepository repository, String arkNaan,
            Collection<String> oaiIds) {
        this.repository = repository;
        this.arkNaan = arkNaan;
        this.oaiIds = List.copyOf(oaiIds);
    }

    @Override
    public int getStartingPage() { return 1; }

    @Override
    public int getTotalPages() { return (oaiIds.size() + pageSize - 1) / pageSize; }

    @Override
    public Page<DarkTrackingRecord> nextPage() {
        var pageable = PageRequest.of(nextPage, pageSize);
        if (nextPage >= getTotalPages()) return Page.empty(pageable);
        int from = nextPage * pageSize;
        List<String> ids = oaiIds.subList(from, Math.min(from + pageSize, oaiIds.size()));
        List<DarkTrackingRecord> records = repository.findByIdArkNaanAndIdOaiIdIn(arkNaan, ids);
        if (records.size() != ids.size()) {
            throw new IllegalStateException("Selected dARK records disappeared during reconciliation");
        }
        nextPage++;
        return new PageImpl<>(records, pageable, oaiIds.size());
    }

    @Override
    public void setPageSize(int size) {
        if (nextPage > 0) throw new IllegalStateException("Cannot change page size after paginator consumption");
        if (size < 1) throw new IllegalArgumentException("Page size must be positive");
        this.pageSize = size;
    }
}
