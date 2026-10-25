package com.taxdoc.planning.rules;

import java.util.SortedSet;

/** A plan was requested for a year with no configured tax constants. Maps to HTTP 422. */
public class UnsupportedTaxYearException extends RuntimeException {

    private final int taxYear;
    private final SortedSet<Integer> supportedYears;

    public UnsupportedTaxYearException(int taxYear, SortedSet<Integer> supportedYears) {
        super("Tax year " + taxYear + " is not configured. Supported years: " + supportedYears);
        this.taxYear = taxYear;
        this.supportedYears = supportedYears;
    }

    public int taxYear() {
        return taxYear;
    }

    public SortedSet<Integer> supportedYears() {
        return supportedYears;
    }
}
