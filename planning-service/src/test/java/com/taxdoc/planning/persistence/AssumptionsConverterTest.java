package com.taxdoc.planning.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain unit test: runs without Docker. */
class AssumptionsConverterTest {

    private final AssumptionsConverter converter = new AssumptionsConverter();

    @Test
    void roundTrips() {
        List<String> assumptions = List.of("Salary is 60% of profit", "Compliance cost $2,000");
        String stored = converter.convertToDatabaseColumn(assumptions);
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(assumptions);
    }

    @Test
    void emptyListIsEmptyText() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }

    @Test
    void rejectsMultiLineAssumptions() {
        assertThatThrownBy(() -> converter.convertToDatabaseColumn(List.of("line one\nline two")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
