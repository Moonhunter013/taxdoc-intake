package com.taxdoc.planning.strategy;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Judgement calls behind the S-corp estimate ({@code taxdoc.strategies.s-corp} in
 * {@code strategy-assumptions.yml}). These are not tax law, so they live apart from
 * {@code tax-years.yml}, and every result lists them.
 *
 * @param salaryRatio share of profit paid as salary, e.g. 0.60
 * @param salaryCap most salary assumed, however high the profit
 * @param annualComplianceCost extra yearly cost of running an S corp (payroll, separate return)
 * @param minimumNetSavings net saving at which the election is recommended rather than marginal
 */
@ConfigurationProperties(prefix = "taxdoc.strategies.s-corp")
public record SCorpAssumptions(
        BigDecimal salaryRatio, BigDecimal salaryCap, BigDecimal annualComplianceCost, BigDecimal minimumNetSavings) {

    public SCorpAssumptions {
        if (salaryRatio == null || salaryRatio.signum() <= 0 || salaryRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "taxdoc.strategies.s-corp.salary-ratio must be a fraction above 0 and at most 1, was " + salaryRatio);
        }
        positive("salary-cap", salaryCap);
        positive("annual-compliance-cost", annualComplianceCost);
        positive("minimum-net-savings", minimumNetSavings);
    }

    private static void positive(String name, BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("taxdoc.strategies.s-corp." + name + " must be positive, was " + value);
        }
    }
}
