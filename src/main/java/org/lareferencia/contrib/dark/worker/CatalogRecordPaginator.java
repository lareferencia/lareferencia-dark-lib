package org.lareferencia.contrib.dark.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.metadata.SnapshotMetadata;
import org.lareferencia.core.repository.catalog.CatalogDatabaseManager;
import org.lareferencia.core.repository.catalog.OAIRecord;
import org.lareferencia.core.worker.IPaginator;
import org.lareferencia.core.worker.PaginatorException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Snapshot-backed paginator for harvested OAI records.
 */
public class CatalogRecordPaginator implements IPaginator<OAIRecord> {

    private static final Logger logger = LogManager.getLogger(CatalogRecordPaginator.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SnapshotMetadata snapshotMetadata;
    private final CatalogDatabaseManager dbManager;
    private final List<String> selectedOaiIds;

    private int pageSize = 100;
    private int maxPages = 0;
    private int currentPage = 0;
    private int totalPages = 0;
    private long totalCount = 0;
    private boolean initialized = false;

    public CatalogRecordPaginator(SnapshotMetadata snapshotMetadata, CatalogDatabaseManager dbManager) {
        this(snapshotMetadata, dbManager, List.of());
    }

    public CatalogRecordPaginator(SnapshotMetadata snapshotMetadata, CatalogDatabaseManager dbManager,
            Collection<String> selectedOaiIds) {
        this.snapshotMetadata = snapshotMetadata;
        this.dbManager = dbManager;
        this.selectedOaiIds = selectedOaiIds == null ? List.of() : selectedOaiIds.stream().distinct().toList();
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
    public Page<OAIRecord> nextPage() {
        ensureInitialized();
        List<OAIRecord> records = queryPage(currentPage, pageSize);
        Page<OAIRecord> page = new PageImpl<>(records, PageRequest.of(currentPage, pageSize), totalCount);
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

    public void setMaxPages(int maxPages) {
        if (initialized) {
            throw new IllegalStateException("Cannot change max pages after paginator initialization");
        }
        this.maxPages = Math.max(0, maxPages);
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        try {
            dbManager.openSnapshotForRead(snapshotMetadata);
        } catch (IOException e) {
            throw new PaginatorException("Unable to open catalog snapshot for dARK staging: " + e.getMessage(), e);
        }

        totalCount = executeCount();
        totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / pageSize);
        if (maxPages > 0) {
            totalPages = Math.min(totalPages, maxPages);
        }
        initialized = true;

        logger.info("DARK catalog paginator initialized for snapshot {} with {} records", snapshotMetadata.getSnapshotId(), totalCount);
    }

    private long executeCount() {
        DataSource dataSource = dbManager.getDataSource(snapshotMetadata.getSnapshotId());
        if (dataSource == null) {
            return 0;
        }

        String sql = "SELECT COUNT(*) FROM oai_record WHERE deleted = 0" + selectedClause();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
        ) {
            bindSelectedOaiIds(statement, 1);
            ResultSet resultSet = statement.executeQuery();
            try (resultSet) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new PaginatorException("Unable to count harvested records for dARK staging", e);
        }
    }

    private List<OAIRecord> queryPage(int page, int size) {
        DataSource dataSource = dbManager.getDataSource(snapshotMetadata.getSnapshotId());
        if (dataSource == null) {
            return List.of();
        }

        List<OAIRecord> records = new ArrayList<>();
        String sql = """
                SELECT id, identifier, datestamp, original_metadata_hash, deleted
                FROM oai_record
                WHERE deleted = 0
                %s
                ORDER BY id
                LIMIT ? OFFSET ?
                """.formatted(selectedClause());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
                int nextParameter = bindSelectedOaiIds(statement, 1);
                statement.setInt(nextParameter++, size);
                statement.setInt(nextParameter, page * size);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    OAIRecord record = new OAIRecord();
                    record.setId(resultSet.getString("id"));
                    record.setIdentifier(resultSet.getString("identifier"));
                    String datestamp = resultSet.getString("datestamp");
                    if (datestamp != null) {
                        record.setDatestamp(LocalDateTime.parse(datestamp, ISO_FORMATTER));
                    }
                    record.setOriginalMetadataHash(resultSet.getString("original_metadata_hash"));
                    record.setDeleted(resultSet.getInt("deleted") == 1);
                    records.add(record);
                }
            }
        } catch (SQLException e) {
            throw new PaginatorException("Unable to read harvested records for dARK staging", e);
        }

        return records;
    }

    private String selectedClause() {
        if (selectedOaiIds.isEmpty()) return "";
        return " AND identifier IN (" + String.join(",", java.util.Collections.nCopies(selectedOaiIds.size(), "?")) + ")";
    }

    private int bindSelectedOaiIds(PreparedStatement statement, int firstParameter) throws SQLException {
        int parameter = firstParameter;
        for (String oaiId : selectedOaiIds) {
            statement.setString(parameter++, oaiId);
        }
        return parameter;
    }
}
