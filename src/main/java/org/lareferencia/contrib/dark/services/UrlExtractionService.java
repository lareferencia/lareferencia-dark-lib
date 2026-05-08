package org.lareferencia.contrib.dark.services;

import org.lareferencia.core.metadata.OAIRecordMetadata;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for extracting the best URL from OAI record metadata.
 * Priority: DOI > Handle > Longest URL
 */
@Service
public class UrlExtractionService {

    /**
     * Extracts the best URL from the record's metadata.
     * Priority: DOI > Handle > Longest URL
     *
     * @param metadata The OAI record metadata
     * @return The best URL found, or empty string if none
     */
    public String extractBestUrl(OAIRecordMetadata metadata) {
        List<String> urls = extractHttpUrls(metadata);
        if (urls.isEmpty()) {
            return "";
        }

        return findDoiUrl(urls)
                .or(() -> findHandleUrl(urls))
                .orElseGet(() -> findLongestUrl(urls));
    }

    public List<String> extractHttpUrls(OAIRecordMetadata metadata) {
        return fieldOccurrences(metadata, "dc.identifier").stream()
                .filter(id -> {
                    String value = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
                    return value.startsWith("http://") || value.startsWith("https://");
                })
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> fieldOccurrences(OAIRecordMetadata metadata, String fieldName) {
        List<String> occurrences = metadata.getFieldOcurrences(fieldName);
        List<String> qualifiedOccurrences = metadata.getFieldOcurrences(fieldName + ".*");
        if (qualifiedOccurrences.isEmpty()) {
            return occurrences;
        }
        return java.util.stream.Stream.concat(occurrences.stream(), qualifiedOccurrences.stream())
                .collect(Collectors.toList());
    }

    private Optional<String> findDoiUrl(List<String> urls) {
        return urls.stream()
                .filter(url -> url.toLowerCase().startsWith("https://doi.org/"))
                .findFirst();
    }

    private Optional<String> findHandleUrl(List<String> urls) {
        return urls.stream()
                .filter(url -> {
                    String normalized = url.toLowerCase();
                    return normalized.contains("/handle/") || normalized.startsWith("https://hdl.handle.net/");
                })
                .findFirst();
    }

    private String findLongestUrl(List<String> urls) {
        return urls.stream()
                .max(Comparator.comparingInt(String::length))
                .map(String::trim)
                .orElse(urls.get(0));
    }
}
