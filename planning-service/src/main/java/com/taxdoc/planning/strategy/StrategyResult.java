package com.taxdoc.planning.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * One strategy's conclusion for one client: the outcome, an estimated annual saving, a plain
 * explanation, and every assumption behind the number (ADR-0007).
 *
 * <p>Use the factory methods. The disclaimer is part of the record so it travels with the result
 * wherever it goes (API, UI, MCP), and the constructor rejects any other text.
 *
 * @param estimatedSavings estimated annual saving in dollars, rounded to cents; zero when not
 *     applicable. For a marginal result it is the (small) positive saving.
 */
public record StrategyResult(
        String code,
        String title,
        Outcome outcome,
        BigDecimal estimatedSavings,
        String explanation,
        List<String> assumptions,
        String disclaimer) {

    public static final String DISCLAIMER =
            "Illustrative estimate based on synthetic data and simplifying assumptions; not tax advice.";

    public StrategyResult {
        if (outcome == null || code == null || title == null || explanation == null) {
            throw new IllegalArgumentException("code, title, outcome and explanation are required");
        }
        if (estimatedSavings == null || estimatedSavings.signum() < 0) {
            throw new IllegalArgumentException("estimatedSavings must be zero or more");
        }
        if (outcome == Outcome.NOT_APPLICABLE && estimatedSavings.signum() != 0) {
            throw new IllegalArgumentException("a strategy that doesn't apply can't claim savings");
        }
        if (!DISCLAIMER.equals(disclaimer)) {
            throw new IllegalArgumentException("results must carry the standard disclaimer");
        }
        estimatedSavings = estimatedSavings.setScale(2, RoundingMode.HALF_UP);
        assumptions = List.copyOf(assumptions);
    }

    public static StrategyResult recommended(
            String code, String title, BigDecimal savings, String explanation, List<String> assumptions) {
        return new StrategyResult(code, title, Outcome.RECOMMENDED, savings, explanation, assumptions, DISCLAIMER);
    }

    public static StrategyResult marginal(
            String code, String title, BigDecimal savings, String explanation, List<String> assumptions) {
        return new StrategyResult(code, title, Outcome.MARGINAL, savings, explanation, assumptions, DISCLAIMER);
    }

    public static StrategyResult notApplicable(
            String code, String title, String explanation, List<String> assumptions) {
        return new StrategyResult(
                code, title, Outcome.NOT_APPLICABLE, BigDecimal.ZERO, explanation, assumptions, DISCLAIMER);
    }
}
