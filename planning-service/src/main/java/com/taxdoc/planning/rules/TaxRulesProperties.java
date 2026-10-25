package com.taxdoc.planning.rules;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All configured tax years, from {@code taxdoc.tax-rules.years} in {@code tax-years.yml}. */
@ConfigurationProperties(prefix = "taxdoc.tax-rules")
public record TaxRulesProperties(Map<Integer, TaxYearRules> years) {

    public TaxRulesProperties {
        if (years == null || years.isEmpty()) {
            throw new IllegalArgumentException(
                    "taxdoc.tax-rules.years is empty: is tax-years.yml imported in application.yml?");
        }
        years = Map.copyOf(years);
    }
}
