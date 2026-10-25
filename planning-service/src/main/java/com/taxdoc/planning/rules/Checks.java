package com.taxdoc.planning.rules;

import java.math.BigDecimal;

/**
 * Validation used by the tax-rule records. A missing or nonsensical value stops the
 * application at startup with the property name in the message, instead of producing a wrong
 * estimate later.
 */
final class Checks {

    private Checks() {}

    static <T> T present(String property, T value) {
        if (value == null) {
            throw new IllegalArgumentException(property + " is missing");
        }
        return value;
    }

    static BigDecimal positive(String property, BigDecimal value) {
        present(property, value);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(property + " must be positive, was " + value);
        }
        return value;
    }

    /** A rate strictly between 0 and 1 (0.062 = 6.2%). */
    static BigDecimal rate(String property, BigDecimal value) {
        present(property, value);
        if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(
                    property + " must be a fraction between 0 and 1, was " + value);
        }
        return value;
    }
}
