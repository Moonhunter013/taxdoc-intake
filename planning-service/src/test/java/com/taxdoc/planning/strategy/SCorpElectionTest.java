package com.taxdoc.planning.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.taxdoc.planning.extraction.ExtractionFixtures;
import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.profile.TaxProfileBuilder;
import com.taxdoc.planning.rules.TaxYearRules;
import com.taxdoc.planning.rules.TaxYearsYaml;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Expected values were worked out by hand (and in a separate Python script with exact decimals),
 * using the 2026 values in tax-years.yml and the defaults in strategy-assumptions.yml: salary 60%
 * of profit capped at $120,000, $2,500 extra costs, recommend from $5,000 net.
 *
 * <p>Sole proprietor tax = Schedule SE (12.4% on net earnings up to the unused wage base, 2.9% on
 * all, net earnings = profit x 0.9235) + 0.9% Additional Medicare above $200,000 less Medicare
 * wages. S-corp tax = 15.3% on the salary (Social Security parts capped as described in
 * PayrollTaxes) + 0.9% employee Additional Medicare.
 */
class SCorpElectionTest {

    private final TaxYearRules y2026 = TaxYearsYaml.load().forYear(2026);
    private final SCorpElection strategy = new SCorpElection(StrategyAssumptionsYaml.sCorp());

    @ParameterizedTest(name = "{0}: {1}, net ${2} (SE tax {3} vs payroll {5} on salary {4})")
    @CsvSource({
        // client-02: profit 185,000. NE 170,847.50 x 15.3% = 26,139.67. Salary 111,000 x 15.3% = 16,983.00.
        //   Saved 9,156.67 - 2,500 = 6,656.67 >= 5,000.
        "client-02, RECOMMENDED, 6656.67, 26139.67, 111000.00, 16983.00",
        // client-09: profit 310,000. NE 286,285: 12.4% x 184,500 = 22,878 + 2.9% x 286,285 = 8,302.27
        //   + 0.9% x 86,285 = 776.57 -> 31,956.83. Salary capped at 120,000 x 15.3% = 18,360.
        //   Saved 13,596.83 - 2,500 = 11,096.83.
        "client-09, RECOMMENDED, 11096.83, 31956.83, 120000.00, 18360.00",
        // client-05: profit 130,000. NE 120,055 x 15.3% = 18,368.42. Salary 78,000 x 15.3% = 11,934.
        //   Saved 6,434.42 - 2,500 = 3,934.42, below 5,000.
        "client-05, MARGINAL, 3934.42, 18368.42, 78000.00, 11934.00",
        // client-07: profit 95,000. NE 87,732.50 x 15.3% = 13,423.07. Salary 57,000 x 15.3% = 8,721.
        //   Saved 4,702.07 - 2,500 = 2,202.07, below 5,000.
        "client-07, MARGINAL, 2202.07, 13423.07, 57000.00, 8721.00",
    })
    void selfEmployedClients(
            String client, Outcome outcome, String net, String soleTax, String salary, String corpTax) {
        StrategyResult result = strategy.evaluate(profile(client), y2026);

        assertThat(result.outcome()).isEqualTo(outcome);
        assertThat(result.estimatedSavings()).isEqualTo(new BigDecimal(net));
        assertThat(result.explanation())
                .contains(Dollars.format(new BigDecimal(soleTax)))
                .contains(Dollars.format(new BigDecimal(salary)) + " salary")
                .contains(Dollars.format(new BigDecimal(corpTax)))
                .contains(Dollars.format(new BigDecimal(net)));
    }

    @Test
    void marginalResultExplainsWhyItIsNotRecommended() {
        StrategyResult result = strategy.evaluate(profile("client-07"), y2026);

        assertThat(result.explanation())
                .contains("below the $5,000")
                .contains("may not justify running payroll");
    }

    @ParameterizedTest(name = "{0} has only W-2 income")
    @ValueSource(strings = {"client-01", "client-04", "client-08", "client-10"})
    void notApplicableWithoutSelfEmploymentIncome(String client) {
        StrategyResult result = strategy.evaluate(profile(client), y2026);

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
        assertThat(result.estimatedSavings()).isEqualByComparingTo("0");
        assertThat(result.explanation()).contains("No self-employment (1099-NEC) income");
    }

    @Test
    void smallSideGigDoesNotCoverTheExtraCosts() {
        // client-03: 42,000 side gig on top of 55,000 W-2. NE 38,787 x 15.3% = 5,934.41; salary 25,200
        // x 15.3% = 3,855.60. Saves 2,078.81 a year, less than the 2,500 of extra costs.
        StrategyResult result = strategy.evaluate(profile("client-03"), y2026);

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
        assertThat(result.explanation()).contains("saves $2,079").contains("outweigh");
    }

    @Test
    void highW2EarnerWouldPayMoreAsAnSCorp() {
        // client-06: W-2 already at the wage base (184,500) and Medicare wages 235,700. Sole proprietor:
        // no Social Security left, 2.9% + 0.9% on NE 32,322.50 = 1,228.26. S-corp on a 21,000 salary:
        // the employer's 6.2% (1,302) is owed regardless, + 2.9% (609) + 0.9% (189) = 2,100.
        // So the S-corp costs 871.75 more even before the extra costs.
        StrategyResult result = strategy.evaluate(profile("client-06"), y2026);

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
        assertThat(result.explanation()).contains("$1,228").contains("$2,100");
    }

    @Test
    void everyResultListsItsAssumptionsAndTheDisclaimer() {
        StrategyResult result = strategy.evaluate(profile("client-02"), y2026);

        assertThat(result.disclaimer()).isEqualTo(StrategyResult.DISCLAIMER).contains("not tax advice");
        assertThat(result.assumptions())
                .anySatisfy(a -> assertThat(a).contains("60% of profit, capped at $120,000"))
                .anySatisfy(a -> assertThat(a).contains("$2,500"))
                .anySatisfy(a -> assertThat(a).contains("single"))
                .anySatisfy(a -> assertThat(a).contains("QBI"))
                .anySatisfy(a -> assertThat(a).contains("treated as net profit"));
    }

    @Test
    void assumptionsComeFromConfiguration() {
        // Same client, a 40% salary instead of 60%: salary 74,000 x 15.3% = 11,322.
        // Saved 26,139.67 - 11,322 = 14,817.67 - 2,500 = 12,317.67.
        var props = StrategyAssumptionsYaml.properties();
        assertThat(props.put("taxdoc.strategies.s-corp.salary-ratio", "0.40")).as("existing key").isNotNull();
        SCorpElection lowerSalary = new SCorpElection(StrategyAssumptionsYaml.sCorp(props));

        StrategyResult result = lowerSalary.evaluate(profile("client-02"), y2026);

        assertThat(result.estimatedSavings()).isEqualTo(new BigDecimal("12317.67"));
        assertThat(result.assumptions()).anySatisfy(a -> assertThat(a).contains("40% of profit"));
    }

    private static TaxProfile profile(String client) {
        return TaxProfileBuilder.build(ExtractionFixtures.documents(client), 2026);
    }
}
