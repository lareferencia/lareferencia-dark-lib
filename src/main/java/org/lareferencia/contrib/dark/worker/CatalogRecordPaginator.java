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
import java.util.List;

/**
 * Snapshot-backed paginator for harvested OAI records.
 */
public class CatalogRecordPaginator implements IPaginator<OAIRecord> {

    private static final Logger logger = LogManager.getLogger(CatalogRecordPaginator.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SnapshotMetadata snapshotMetadata;
    private final CatalogDatabaseManager dbManager;

    private int pageSize = 100;
    private int currentPage = 0;
    private int totalPages = 0;
    private long totalCount = 0;
    private boolean initialized = false;

    public CatalogRecordPaginator(SnapshotMetadata snapshotMetadata, CatalogDatabaseManager dbManager) {
        this.snapshotMetadata = snapshotMetadata;
        this.dbManager = dbManager;
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
        initialized = true;

        logger.info("DARK catalog paginator initialized for snapshot {} with {} records", snapshotMetadata.getSnapshotId(), totalCount);
    }

    private long executeCount() {
        DataSource dataSource = dbManager.getDataSource(snapshotMetadata.getSnapshotId());
        if (dataSource == null) {
            return 0;
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM oai_record WHERE deleted = 0");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
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
                ORDER BY id
                LIMIT ? OFFSET ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, size);
            statement.setInt(2, page * size);

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
}
