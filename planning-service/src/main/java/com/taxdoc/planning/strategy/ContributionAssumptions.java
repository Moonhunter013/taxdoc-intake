package com.taxdoc.planning.strategy;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Judgement calls shared by the contribution strategies (workplace 401(k), Solo 401(k), HSA):
 * {@code taxdoc.strategies.contributions} in {@code strategy-assumptions.yml}.
 *
 * @param savingsRate share of pay a client is assumed to save for retirement, e.g. 0.15
 * @param minimumSavings estimated tax saved at which a strategy is recommended rather than marginal
 */
@ConfigurationProperties(prefix = "taxdoc.strategies.contributions")
public record ContributionAssumptions(BigDecimal savingsRate, BigDecimal minimumSavings) {

    public ContributionAssumptions {
        if (savingsRate == null || savingsRate.signum() <= 0 || savingsRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "taxdoc.strategies.contributions.savings-rate must be a fraction above 0 and at most 1, was "
                            + savingsRate);
        }
        if (minimumSavings == null || minimumSavings.signum() <= 0) {
            throw new IllegalArgumentException(
                    "taxdoc.strategies.contributions.minimum-savings must be positive, was " + minimumSavings);
        }
    }

    /** Recommended at or above the threshold, marginal (with the reason) below it. Callers handle zero. */
    StrategyResult result(String code, String title, BigDecimal saved, String explanation, List<String> assumptions) {
        if (saved.compareTo(minimumSavings) >= 0) {
            return StrategyResult.recommended(code, title, saved, explanation, assumptions);
        }
        return StrategyResult.marginal(code, title, saved, explanation + " That's below the "
                + Dollars.format(minimumSavings) + " this engine requires to recommend it.", assumptions);
    }
}
