package org.lareferencia.contrib.dark.services;

import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DarkLevel1MetadataService {

    public static final String MAPPING_VERSION = "l1-v2";

    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");
    private static final Pattern DOI_PATTERN = Pattern.compile("(?i)(?:https?://(?:dx\\.)?doi\\.org/|doi:\\s*)?(10\\.\\d{4,9}/\\S+)");
    private static final Pattern HANDLE_URL_PATTERN = Pattern.compile(
            "(?i)https?://(?:(?:hdl\\.)?handle\\.net/|[^/]+/handle/)(\\S+)");
    private static final Map<String, String> ISO3_TO_ISO2 = buildIso3ToIso2();

    public Map<String, Object> buildMinimalMetadata(String oaiId, OAIRecordMetadata metadata, String targetUrl) {
        String title = firstNonBlank(fieldOccurrences(metadata, "dc.title"))
                .orElseThrow(() -> new IllegalArgumentException("Missing dc.title"));
        List<String> authors = normalizeAuthors(fieldOccurrences(metadata, "dc.creator"));
        if (authors.isEmpty()) {
            authors = normalizeAuthors(fieldOccurrences(metadata, "dc.contributor.author"));
        }
        if (authors.isEmpty()) {
            throw new IllegalArgumentException("Missing dc.creator and dc.contributor.author");
        }

        Integer year = extractYear(dateOccurrences(metadata))
                .orElseThrow(() -> new IllegalArgumentException("Missing parseable dc.date year"));

        LinkedHashMap<String, Object> minimalMetadata = new LinkedHashMap<>();
        minimalMetadata.put("title", title);
        minimalMetadata.put("authors", authors);
        minimalMetadata.put("year", year);

        putIfPresent(minimalMetadata, "publisher", firstNonBlank(fieldOccurrences(metadata, "dc.publisher")).orElse(null));
        putIfPresent(minimalMetadata, "resource_type", firstNonBlank(fieldOccurrences(metadata, "dc.type")).orElse(null));
        putIfPresent(minimalMetadata, "language", normalizeLanguage(firstNonBlank(fieldOccurrences(metadata, "dc.language")).orElse(null)));
        putIfPresent(minimalMetadata, "abstract", extractAbstract(metadata));

        List<String> subjects = dedupe(fieldOccurrences(metadata, "dc.subject"));
        if (!subjects.isEmpty()) {
            minimalMetadata.put("subjects", subjects);
        }

        putIfPresent(minimalMetadata, "rights", firstNonBlank(fieldOccurrences(metadata, "dc.rights")).orElse(null));

        List<Map<String, String>> alternateIdentifiers = buildAlternateIdentifiers(oaiId, metadata, targetUrl);
        if (!alternateIdentifiers.isEmpty()) {
            minimalMetadata.put("alternate_identifiers", alternateIdentifiers);
        }

        List<String> alternateUrls = buildAlternateUrls(metadata, targetUrl);
        if (!alternateUrls.isEmpty()) {
            minimalMetadata.put("alternate_urls", alternateUrls);
        }

        return minimalMetadata;
    }

    private List<Map<String, String>> buildAlternateIdentifiers(String oaiId, OAIRecordMetadata metadata, String targetUrl) {
        LinkedHashMap<String, Map<String, String>> identifiers = new LinkedHashMap<>();
        addIdentifier(identifiers, "oai", oaiId);

        for (String value : dedupe(fieldOccurrences(metadata, "dc.identifier"))) {
            String normalizedIdentifier = normalizePersistentIdentifier(value);
            if (normalizedIdentifier != null) {
                addIdentifier(identifiers, inferSchema(normalizedIdentifier), normalizedIdentifier);
                continue;
            }
            if (value.equals(targetUrl) || isHttpUrl(value)) {
                continue;
            }
            addIdentifier(identifiers, inferSchema(value), value.trim());
        }

        return new ArrayList<>(identifiers.values());
    }

    private List<String> buildAlternateUrls(OAIRecordMetadata metadata, String targetUrl) {
        Set<String> urls = new LinkedHashSet<>();
        for (String value : fieldOccurrences(metadata, "dc.identifier")) {
            if (!isHttpUrl(value) || normalizePersistentIdentifier(value) != null) {
                continue;
            }
            String normalized = value.trim();
            if (normalized.equals(targetUrl)) {
                continue;
            }
            urls.add(normalized);
        }
        return new ArrayList<>(urls);
    }

    private void addIdentifier(Map<String, Map<String, String>> identifiers, String schema, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalizedValue = value.trim();
        String key = schema + "::" + normalizedValue;
        identifiers.putIfAbsent(key, Map.of("schema", schema, "value", normalizedValue));
    }

    private String inferSchema(String identifier) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("10.") || normalized.contains("doi.org/")) {
            return "doi";
        }
        if (normalized.startsWith("oai:")) {
            return "oai";
        }
        if (normalized.startsWith("ark:")) {
            return "ark";
        }
        if (normalized.contains("/handle/") || normalized.startsWith("hdl:")) {
            return "handle";
        }
        return "other";
    }

    private Optional<Integer> extractYear(List<String> values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            Matcher matcher = YEAR_PATTERN.matcher(value);
            if (matcher.find()) {
                return Optional.of(Integer.parseInt(matcher.group()));
            }
        }
        return Optional.empty();
    }

    private String extractAbstract(OAIRecordMetadata metadata) {
        Optional<String> preferred = firstNonBlank(fieldOccurrences(metadata, "dc.description.abstract"));
        if (preferred.isPresent()) {
            return preferred.get();
        }
        return firstNonBlank(exactOccurrences(metadata, "dc.description")).orElse(null);
    }

    private List<String> fieldOccurrences(OAIRecordMetadata metadata, String fieldName) {
        LinkedHashSet<String> result = new LinkedHashSet<>(exactOccurrences(metadata, fieldName));
        result.addAll(safeOccurrences(metadata, fieldName + ".*"));
        return new ArrayList<>(result);
    }

    public int countFieldOccurrences(OAIRecordMetadata metadata, String fieldName) {
        return fieldOccurrences(metadata, fieldName).size();
    }

    private List<String> exactOccurrences(OAIRecordMetadata metadata, String fieldName) {
        return new ArrayList<>(new LinkedHashSet<>(safeOccurrences(metadata, fieldName)));
    }

    private List<String> safeOccurrences(OAIRecordMetadata metadata, String selector) {
        List<String> values = metadata.getFieldOcurrences(selector);
        return values != null ? values : List.of();
    }

    private List<String> dateOccurrences(OAIRecordMetadata metadata) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(fieldOccurrences(metadata, "dc.date.issued"));
        values.addAll(fieldOccurrences(metadata, "dc.date.created"));
        values.addAll(fieldOccurrences(metadata, "dc.date"));
        return new ArrayList<>(values);
    }

    private Optional<String> firstNonBlank(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst();
    }

    private List<String> dedupe(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return new ArrayList<>(result);
    }

    private List<String> normalizeAuthors(List<String> values) {
        LinkedHashSet<String> authors = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String name = value.split("\\|\\|\\|", 2)[0].trim();
            if (!name.isBlank()) {
                authors.add(name);
            }
        }
        return new ArrayList<>(authors);
    }

    private String normalizeLanguage(String rawLanguage) {
        if (rawLanguage == null || rawLanguage.isBlank()) {
            return null;
        }
        String value = rawLanguage.trim().toLowerCase(Locale.ROOT);
        String primary = value.split("[-_]", 2)[0];
        if (primary.length() == 2) {
            return primary;
        }
        return ISO3_TO_ISO2.getOrDefault(primary, primary);
    }

    private String normalizePersistentIdentifier(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            return null;
        }
        String value = rawIdentifier.trim();
        Matcher doiMatcher = DOI_PATTERN.matcher(value);
        if (doiMatcher.matches()) {
            return doiMatcher.group(1).replaceAll("[\\s.,;]+$", "");
        }
        Matcher handleUrlMatcher = HANDLE_URL_PATTERN.matcher(value);
        if (handleUrlMatcher.matches()) {
            return "hdl:" + handleUrlMatcher.group(1).replaceAll("[\\s.,;]+$", "");
        }
        if (value.regionMatches(true, 0, "hdl:", 0, 4)) {
            return "hdl:" + value.substring(4).trim();
        }
        return null;
    }

    private static Map<String, String> buildIso3ToIso2() {
        Map<String, String> result = new HashMap<>();
        Arrays.stream(Locale.getISOLanguages()).forEach(language -> {
            Locale locale = Locale.forLanguageTag(language);
            try {
                result.put(locale.getISO3Language().toLowerCase(Locale.ROOT), language);
            } catch (Exception ignored) {
                // Ignore incomplete locale entries supplied by the runtime.
            }
        });
        return result;
    }

    private boolean isHttpUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        target.put(key, value);
    }
}
