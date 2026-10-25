package com.taxdoc.planning.rules;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Looks up the constants for a tax year. There is deliberately no "closest year" fallback:
 * using 2026 limits for a 2027 plan would give a confident wrong answer, so an unconfigured
 * year is an error the caller has to handle.
 */
@Service
@EnableConfigurationProperties(TaxRulesProperties.class)
public class TaxRules {

    private final TaxRulesProperties properties;

    public TaxRules(TaxRulesProperties properties) {
        this.properties = properties;
    }

    public TaxYearRules forYear(int taxYear) {
        TaxYearRules rules = properties.years().get(taxYear);
        if (rules == null) {
            throw new UnsupportedTaxYearException(taxYear, supportedYears());
        }
        return rules;
    }

    public SortedSet<Integer> supportedYears() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(properties.years().keySet()));
    }
}
