package org.lareferencia.contrib.dark.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DarkTrackingStateConverter implements AttributeConverter<DarkTrackingState, String> {

    @Override
    public String convertToDatabaseColumn(DarkTrackingState attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public DarkTrackingState convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        for (DarkTrackingState candidate : DarkTrackingState.values()) {
            if (candidate.getValue().equalsIgnoreCase(dbData)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown dARK tracking state: " + dbData);
    }
}
