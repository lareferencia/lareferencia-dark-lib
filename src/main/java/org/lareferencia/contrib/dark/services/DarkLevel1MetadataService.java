package org.lareferencia.contrib.dark.services;

import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    private static final Pattern YEAR_PATTERN = Pattern.compile("(19|20)\\d{2}");

    public Map<String, Object> buildMinimalMetadata(String oaiId, OAIRecordMetadata metadata, String targetUrl) {
        String title = firstNonBlank(metadata.getFieldOcurrences("dc.title.*"))
                .orElseThrow(() -> new IllegalArgumentException("Missing dc.title"));
        List<String> authors = dedupe(metadata.getFieldOcurrences("dc.creator.*"));
        if (authors.isEmpty()) {
            throw new IllegalArgumentException("Missing dc.creator");
        }

        Integer year = extractYear(metadata.getFieldOcurrences("dc.date.*"))
                .orElseThrow(() -> new IllegalArgumentException("Missing parseable dc.date year"));

        LinkedHashMap<String, Object> minimalMetadata = new LinkedHashMap<>();
        minimalMetadata.put("title", title);
        minimalMetadata.put("authors", authors);
        minimalMetadata.put("year", year);

        putIfPresent(minimalMetadata, "publisher", firstNonBlank(metadata.getFieldOcurrences("dc.publisher.*")).orElse(null));
        putIfPresent(minimalMetadata, "resource_type", firstNonBlank(metadata.getFieldOcurrences("dc.type.*")).orElse(null));
        putIfPresent(minimalMetadata, "language", normalizeLanguage(firstNonBlank(metadata.getFieldOcurrences("dc.language.*")).orElse(null)));
        putIfPresent(minimalMetadata, "abstract", extractAbstract(metadata));

        List<String> subjects = dedupe(metadata.getFieldOcurrences("dc.subject.*"));
        if (!subjects.isEmpty()) {
            minimalMetadata.put("subjects", subjects);
        }

        putIfPresent(minimalMetadata, "rights", firstNonBlank(metadata.getFieldOcurrences("dc.rights.*")).orElse(null));

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

        for (String value : dedupe(metadata.getFieldOcurrences("dc.identifier.*"))) {
            if (value.equals(targetUrl) || isHttpUrl(value)) {
                continue;
            }
            addIdentifier(identifiers, inferSchema(value), value.trim());
        }

        return new ArrayList<>(identifiers.values());
    }

    private List<String> buildAlternateUrls(OAIRecordMetadata metadata, String targetUrl) {
        Set<String> urls = new LinkedHashSet<>();
        for (String value : metadata.getFieldOcurrences("dc.identifier.*")) {
            if (!isHttpUrl(value)) {
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
        Optional<String> preferred = firstNonBlank(metadata.getFieldOcurrences("dc.description.abstract.*"));
        if (preferred.isPresent()) {
            return preferred.get();
        }
        return firstNonBlank(metadata.getFieldOcurrences("dc.description.*")).orElse(null);
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

    private String normalizeLanguage(String rawLanguage) {
        if (rawLanguage == null || rawLanguage.isBlank()) {
            return null;
        }
        String value = rawLanguage.trim();
        if (value.length() >= 2) {
            return value.substring(0, 2).toLowerCase(Locale.ROOT);
        }
        return value.toLowerCase(Locale.ROOT);
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
