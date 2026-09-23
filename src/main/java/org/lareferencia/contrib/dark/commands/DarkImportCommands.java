package org.lareferencia.contrib.dark.commands;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecord;
import org.lareferencia.contrib.dark.domain.DarkTrackingRecordId;
import org.lareferencia.contrib.dark.domain.DarkTrackingState;
import org.lareferencia.contrib.dark.repositories.DarkTrackingRepository;
import org.lareferencia.contrib.dark.services.DarkArkIdentifier;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Administrative commands for importing dARK records created by the legacy minter integration. */
@ShellComponent
public class DarkImportCommands {

    private static final Pattern ARK_PATTERN = Pattern.compile("^ark:([^/\\s]+)/.+$");
    private static final DateTimeFormatter LEGACY_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ROOT);
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "darkidentifier", "oaiidentifier", "datestamp", "itemurl", "lastmodified");

    private final DarkTrackingRepository repository;

    public DarkImportCommands(DarkTrackingRepository repository) {
        this.repository = repository;
    }

    @ShellMethod(value = "Import legacy dARK ARK/OAI mappings from CSV. Use --apply to persist; without it the command only validates and reports.",
            key = "import-dark-legacy-csv")
    @Transactional
    public String importLegacyCsv(
            @ShellOption(value = "--path", help = "Full path to the legacy CSV file") String csvPath,
            @ShellOption(value = "--apply", help = "Persist validated rows", defaultValue = "false") boolean apply) {
        ImportInput input;
        try {
            input = readAndValidate(Path.of(csvPath));
        } catch (IllegalArgumentException | IOException | CsvValidationException e) {
            return "ERROR: " + e.getMessage();
        }

        ImportSummary summary = inspectDatabase(input.records);
        if (!summary.conflicts.isEmpty()) {
            return formatFailure("The import was not applied because conflicts were found", summary);
        }
        if (!apply) {
            return formatDryRun(summary);
        }

        for (LegacyRecord source : summary.toInsert) {
            DarkTrackingRecord record = new DarkTrackingRecord();
            record.setId(DarkTrackingRecordId.of(source.naan, source.oaiId));
            record.setArk(source.ark);
            record.setTargetUrl(source.itemUrl);
            record.setState(DarkTrackingState.UPDATE);
            record.setCreatedAt(source.datestamp);
            record.setUpdatedAt(source.lastModified);
            // Hashes and lastStagedAt remain empty deliberately: the first stage sends full metadata.
            repository.save(record);
        }
        return String.format("Imported %d legacy dARK mappings; %d matching rows already existed. "
                        + "All imported records are in UPDATE state and will send metadata on their first DARK stage run.",
                summary.toInsert.size(), summary.alreadyPresent);
    }

    private ImportInput readAndValidate(Path path) throws IOException, CsvValidationException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("CSV file is not readable: " + path);
        }

        try (Reader fileReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVReader csv = new CSVReader(fileReader)) {
            String[] header = csv.readNext();
            Map<String, Integer> columns = indexHeaders(header);
            List<LegacyRecord> records = new ArrayList<>();
            Set<DarkTrackingRecordId> ids = new HashSet<>();
            Set<String> arks = new HashSet<>();
            String[] row;
            int line = 1;
            while ((row = csv.readNext()) != null) {
                line++;
                if (isBlankRow(row)) {
                    continue;
                }
                LegacyRecord record = toLegacyRecord(row, columns, line);
                DarkTrackingRecordId id = DarkTrackingRecordId.of(record.naan, record.oaiId);
                if (!ids.add(id)) {
                    throw new IllegalArgumentException("Duplicate ARK NAAN/OAI identifier at CSV line " + line);
                }
                if (!arks.add(record.ark)) {
                    throw new IllegalArgumentException("Duplicate ARK at CSV line " + line + ": " + record.ark);
                }
                records.add(record);
            }
            return new ImportInput(records);
        }
    }

    private Map<String, Integer> indexHeaders(String[] header) {
        if (header == null) {
            throw new IllegalArgumentException("CSV is empty; expected a header row");
        }
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.length; index++) {
            String name = normalize(header[index]);
            if (index == 0 && name.startsWith("\uFEFF")) {
                name = name.substring(1);
            }
            if (!name.isEmpty()) {
                columns.put(name.toLowerCase(Locale.ROOT), index);
            }
        }
        if (!columns.keySet().containsAll(REQUIRED_HEADERS)) {
            throw new IllegalArgumentException("CSV must contain headers: " + String.join(", ", REQUIRED_HEADERS));
        }
        return columns;
    }

    private LegacyRecord toLegacyRecord(String[] row, Map<String, Integer> columns, int line) {
        String ark = DarkArkIdentifier.normalize(field(row, columns, "darkidentifier", line, true));
        Matcher matcher = ARK_PATTERN.matcher(ark);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid ARK at CSV line " + line + ": " + ark);
        }
        String oaiId = field(row, columns, "oaiidentifier", line, true);
        LocalDateTime datestamp = parseTimestamp(field(row, columns, "datestamp", line, true), "datestamp", line);
        LocalDateTime lastModified = parseTimestamp(field(row, columns, "lastmodified", line, true), "lastmodified", line);
        String itemUrl = field(row, columns, "itemurl", line, false);
        return new LegacyRecord(ark, matcher.group(1), oaiId, itemUrl, datestamp, lastModified);
    }

    private ImportSummary inspectDatabase(List<LegacyRecord> records) {
        ImportSummary summary = new ImportSummary();
        for (LegacyRecord source : records) {
            DarkTrackingRecordId id = DarkTrackingRecordId.of(source.naan, source.oaiId);
            DarkTrackingRecord byId = repository.findById(id).orElse(null);
            DarkTrackingRecord byArk = repository.findByArk(source.ark).orElse(null);
            if (byId == null && byArk == null) {
                summary.toInsert.add(source);
            } else if (byId != null && source.ark.equals(byId.getArk())
                    && (byArk == null || id.equals(byArk.getId()))) {
                summary.alreadyPresent++;
            } else {
                summary.conflicts.add("ARK " + source.ark + " / OAI " + source.oaiId
                        + " conflicts with an existing tracking record");
            }
        }
        return summary;
    }

    private String formatDryRun(ImportSummary summary) {
        return String.format("Dry run successful: %d rows would be imported as UPDATE; %d matching rows would be skipped. "
                        + "Run again with --apply to persist.", summary.toInsert.size(), summary.alreadyPresent);
    }

    private String formatFailure(String prefix, ImportSummary summary) {
        StringBuilder result = new StringBuilder(prefix).append(": ").append(summary.conflicts.size()).append(" conflict(s).");
        for (String conflict : summary.conflicts.subList(0, Math.min(10, summary.conflicts.size()))) {
            result.append(System.lineSeparator()).append("- ").append(conflict);
        }
        if (summary.conflicts.size() > 10) {
            result.append(System.lineSeparator()).append("- ...");
        }
        return result.toString();
    }

    private String field(String[] row, Map<String, Integer> columns, String header, int line, boolean required) {
        int index = columns.get(header);
        String value = index < row.length ? normalize(row[index]) : "";
        if (required && value.isEmpty()) {
            throw new IllegalArgumentException("Missing " + header + " at CSV line " + line);
        }
        return value;
    }

    private LocalDateTime parseTimestamp(String value, String header, int line) {
        try {
            return LocalDateTime.parse(value, LEGACY_TIMESTAMP);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid " + header + " at CSV line " + line + ": " + value);
        }
    }

    private boolean isBlankRow(String[] row) {
        for (String value : row) {
            if (!normalize(value).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static class ImportInput {
        private final List<LegacyRecord> records;

        private ImportInput(List<LegacyRecord> records) {
            this.records = records;
        }
    }

    private static class ImportSummary {
        private final List<LegacyRecord> toInsert = new ArrayList<>();
        private final List<String> conflicts = new ArrayList<>();
        private int alreadyPresent;
    }

    private static class LegacyRecord {
        private final String ark;
        private final String naan;
        private final String oaiId;
        private final String itemUrl;
        private final LocalDateTime datestamp;
        private final LocalDateTime lastModified;

        private LegacyRecord(String ark, String naan, String oaiId, String itemUrl,
                             LocalDateTime datestamp, LocalDateTime lastModified) {
            this.ark = ark;
            this.naan = naan;
            this.oaiId = oaiId;
            this.itemUrl = itemUrl;
            this.datestamp = datestamp;
            this.lastModified = lastModified;
        }
    }
}
