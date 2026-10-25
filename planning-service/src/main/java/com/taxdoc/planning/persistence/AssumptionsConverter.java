package com.taxdoc.planning.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;

/**
 * Stores a strategy's assumptions as newline-separated text.
 *
 * <p>Deliberately simple: assumptions are short sentences, and a plain VARCHAR keeps the schema
 * portable and easy for Hibernate to validate. An assumption containing a newline is rejected
 * rather than silently split into two.
 */
@Converter
public class AssumptionsConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> assumptions) {
        if (assumptions == null || assumptions.isEmpty()) {
            return "";
        }
        for (String assumption : assumptions) {
            if (assumption.contains("\n")) {
                throw new IllegalArgumentException("Assumptions must be single-line: " + assumption);
            }
        }
        return String.join("\n", assumptions);
    }

    @Override
    public List<String> convertToEntityAttribute(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(text.split("\n"));
    }
}
