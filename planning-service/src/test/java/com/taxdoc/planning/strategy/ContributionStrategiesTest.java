package com.taxdoc.planning.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.taxdoc.planning.extraction.ExtractionFixtures;
import com.taxdoc.planning.profile.TaxProfile;
import com.taxdoc.planning.profile.TaxProfile.W2SocialSecurity;
import com.taxdoc.planning.profile.TaxProfileBuilder;
import com.taxdoc.planning.rules.TaxYearRules;
import com.taxdoc.planning.rules.TaxYearsYaml;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Workplace 401(k), Solo 401(k), HSA and excess Social Security (#23). Expected values were worked
 * out by hand and in a separate Python script with exact decimals, using tax-years.yml for 2026 and
 * strategy-assumptions.yml: save 15% of pay, recommend from $1,000. Filing status single, so taxable
 * income = W-2 box 1 + 1099 income - half SE tax - 16,100.
 */
class ContributionStrategiesTest {

    private static final TaxYearRules Y2026 = TaxYearsYaml.load().forYear(2026);
    private static final ContributionAssumptions ASSUMPTIONS = StrategyAssumptionsYaml.contributions();

    @Nested
    class Workplace401k {

        private final WorkplaceRetirement strategy = new WorkplaceRetirement(ASSUMPTIONS);

        @ParameterizedTest(name = "{0}: add {2} -> {1} ${3}")
        @CsvSource({
            // client-01: target 15% x 212,000 = 31,800; room 24,500 - 9,000 = 15,500 (the smaller).
            //   Taxable 186,900 is in the 24% bracket: 15,500 x 24% = 3,720.
            "client-01, RECOMMENDED, 15500, 3720.00",
            // client-10: target 15% x 194,500 = 29,175; room 24,500 - 14,000 = 10,500; 24% -> 2,520.
            "client-10, RECOMMENDED, 10500, 2520.00",
            // client-03: target 15% x 55,000 = 8,250 - 2,750 = 5,500; taxable 75,183 at 22% -> 1,210.
            "client-03, RECOMMENDED, 5500, 1210.00",
            // client-04 (control): 15% x 65,000 = 9,750 - 6,800 = 2,950; taxable 42,100 at 12% -> 354.
            "client-04, MARGINAL, 2950, 354.00",
            // client-08 (control): 15% x 38,000 = 5,700; taxable 21,900 at 12% -> 684.
            "client-08, MARGINAL, 5700, 684.00",
            // client-06: only 1,000 of room left (23,500 deferred); 32% -> 320.
            "client-06, MARGINAL, 1000, 320.00",
        })
        void headroomTowardTheSavingsTarget(String client, Outcome outcome, String added, String saved) {
            StrategyResult result = strategy.evaluate(profile(client), Y2026);

            assertThat(result.outcome()).isEqualTo(outcome);
            assertThat(result.estimatedSavings()).isEqualTo(new BigDecimal(saved));
            assertThat(result.explanation()).contains("Adding " + Dollars.format(new BigDecimal(added)));
        }

        @ParameterizedTest(name = "{0} has no W-2")
        @ValueSource(strings = {"client-02", "client-09"})
        void notApplicableWithoutAW2(String client) {
            assertThat(strategy.evaluate(profile(client), Y2026).outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
        }

        @Test
        void notApplicableAtTheDeferralLimit() {
            StrategyResult result = strategy.evaluate(w2Profile("150000.00", "24500.00", "0"), Y2026);

            assertThat(result.outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
            assertThat(result.explanation()).contains("the annual limit");
        }

        @Test
        void notApplicableWhenAlreadyAtTheSavingsTarget() {
            // 15% x 100,000 = 15,000, already deferred; room under the limit doesn't matter.
            StrategyResult result = strategy.evaluate(w2Profile("100000.00", "15000.00", "0"), Y2026);

            assertThat(result.outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
            assertThat(result.explanation()).contains("at least 15% of pay");
        }
    }

    @Nested
    class Solo401k {

        private final SelfEmployedRetirement strategy = new SelfEmployedRetirement(ASSUMPTIONS);

        @ParameterizedTest(name = "{0}: contribute {2} of max {3} -> {1} ${4}")
        @CsvSource({
            // client-02: plan earnings 185,000 - 13,069.84 (half SE tax) = 171,930.17; employer 20% = 34,386.03
            //   + employee 24,500 = 58,886.03 max. Target 15% x 185,000 = 27,750 at 24% -> 6,660.
            "client-02, RECOMMENDED, 27750, 58886.03, 6660.00",
            // client-09: employer 20% x 294,409.85 = 58,881.97 + 24,500, capped at the 72,000 annual additions.
            //   Target 46,500: taxable 278,309.87 -> 231,809.87 crosses 256,225: 22,084.87 x 35% + 24,415.13 x 32%.
            "client-09, RECOMMENDED, 46500, 72000.00, 15542.54",
            // client-05: employer 24,163.16 + 24,500 = 48,663.16. Target 19,500 at 22% -> 4,290.
            "client-05, RECOMMENDED, 19500, 48663.16, 4290.00",
            // client-07: employer 17,657.69 + 24,500 = 42,157.69. Target 14,250 at 22% -> 3,135.
            "client-07, RECOMMENDED, 14250, 42157.69, 3135.00",
            // client-03 (side gig + W-2): employer part only, 7,806.56; target 6,300 at 22% -> 1,386.
            "client-03, RECOMMENDED, 6300, 7806.56, 1386.00",
            // client-06 (W-2 + consulting): employer part only, 6,906.26; target 5,250 at 32% -> 1,680.
            "client-06, RECOMMENDED, 5250, 6906.26, 1680.00",
        })
        void contributionTowardTheSavingsTarget(
                String client, Outcome outcome, String contributed, String maximum, String saved) {
            StrategyResult result = strategy.evaluate(profile(client), Y2026);

            assertThat(result.outcome()).isEqualTo(outcome);
            assertThat(result.estimatedSavings()).isEqualTo(new BigDecimal(saved));
            // "could take up to", not just "up to": the SEP sentence also says "up to" and, for clients
            // with a W-2, shows the same employer-only figure, which once let a wrong maximum pass.
            assertThat(result.explanation())
                    .contains("could take up to " + Dollars.format(new BigDecimal(maximum)) + " of")
                    .contains("Contributing " + Dollars.format(new BigDecimal(contributed)));
        }

        @Test
        void clientsWithAW2LeaveTheSharedDeferralRoomToTheWorkplacePlan() {
            StrategyResult result = strategy.evaluate(profile("client-03"), Y2026);

            assertThat(result.explanation()).doesNotContain("as the employee");
            assertThat(result.assumptions()).anySatisfy(a -> assertThat(a).contains("shared with the W-2 401(k)"));
        }

        @Test
        void explanationOffersSepAsTheSimplerAlternative() {
            // SEP = employer part only: 34,386.03 for client-02.
            assertThat(strategy.evaluate(profile("client-02"), Y2026).explanation())
                    .contains("SEP-IRA is simpler").contains("up to $34,386");
        }

        @ParameterizedTest(name = "{0} has no self-employment income")
        @ValueSource(strings = {"client-01", "client-04", "client-08", "client-10"})
        void notApplicableWithoutSelfEmploymentIncome(String client) {
            assertThat(strategy.evaluate(profile(client), Y2026).outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
        }
    }

    @Nested
    class Hsa {

        private final HsaContribution strategy = new HsaContribution(ASSUMPTIONS);

        @ParameterizedTest(name = "{0}: add {2} -> {1} ${3}")
        @CsvSource({
            // client-01: 4,400 x 24% = 1,056 income tax. Pay 212,000 stays above the wage base, so no Social
            //   Security saved; Medicare 1.45% + Additional Medicare 0.9% (wages over 200,000) = 103.40.
            "client-01, RECOMMENDED, 4400, 1159.40",
            // client-03: 4,400 x 22% = 968 + 7.65% x 4,400 = 336.60.
            "client-03, RECOMMENDED, 4400, 1304.60",
            // client-08 (control): 4,400 x 12% = 528 + 336.60 = 864.60, below 1,000.
            "client-08, MARGINAL, 4400, 864.60",
            // client-04 (control): already 3,000; 1,400 x 12% = 168 + 7.65% = 107.10.
            "client-04, MARGINAL, 1400, 275.10",
            // client-10: already 1,500; 2,900 x 24% = 696 + Medicare 42.05 (pay 194,500 stays over the base).
            "client-10, MARGINAL, 2900, 738.05",
        })
        void roomUpToTheSelfOnlyLimit(String client, Outcome outcome, String added, String saved) {
            StrategyResult result = strategy.evaluate(profile(client), Y2026);

            assertThat(result.outcome()).isEqualTo(outcome);
            assertThat(result.estimatedSavings()).isEqualTo(new BigDecimal(saved));
            assertThat(result.explanation()).contains("Adding " + Dollars.format(new BigDecimal(added)));
        }

        @Test
        void eligibilityIsAlwaysStatedAsAnAssumption() {
            assertThat(strategy.evaluate(profile("client-01"), Y2026).assumptions())
                    .anySatisfy(a -> assertThat(a).contains("high-deductible health plan").contains("check this first"));
        }

        @Test
        void notApplicableAtTheLimit() {
            StrategyResult result = strategy.evaluate(w2Profile("80000.00", "0", "4400.00"), Y2026);

            assertThat(result.outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
            assertThat(result.explanation()).contains("the self-only limit");
        }

        @Test
        void notApplicableWithoutAW2Employer() {
            assertThat(strategy.evaluate(profile("client-02"), Y2026).outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
        }
    }

    @Nested
    class ExcessSocialSecurityCredit {

        private final ExcessSocialSecurity strategy = new ExcessSocialSecurity();

        @Test
        void twoEmployersTogetherWithheldTooMuch() {
            // client-10: 6,944 + 5,115 = 12,059 withheld; maximum 184,500 x 6.2% = 11,439; excess 620.
            StrategyResult result = strategy.evaluate(profile("client-10"), Y2026);

            assertThat(result.outcome()).isEqualTo(Outcome.RECOMMENDED);
            assertThat(result.estimatedSavings()).isEqualTo(new BigDecimal("620.00"));
            assertThat(result.explanation()).contains("$12,059").contains("$11,439").contains("refundable credit");
        }

        @ParameterizedTest(name = "{0} has one W-2 or none")
        @ValueSource(strings = {"client-01", "client-02", "client-04", "client-08"})
        void notApplicableWithFewerThanTwoW2s(String client) {
            assertThat(strategy.evaluate(profile(client), Y2026).outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
        }

        @Test
        void twoW2sWithinTheMaximumAreNotApplicable() {
            TaxProfile profile = profileWithW2s(
                    new W2SocialSecurity(UUID.randomUUID(), "A", new BigDecimal("50000"), new BigDecimal("3100")),
                    new W2SocialSecurity(UUID.randomUUID(), "B", new BigDecimal("50000"), new BigDecimal("3100")));

            StrategyResult result = strategy.evaluate(profile, Y2026);

            assertThat(result.outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
            assertThat(result.explanation()).contains("within the $11,439 maximum");
        }

        @Test
        void oneEmployerOverWithholdingIsForThatEmployerToFix() {
            TaxProfile profile = profileWithW2s(
                    new W2SocialSecurity(UUID.randomUUID(), "Overwithholding Co", new BigDecimal("200000"),
                            new BigDecimal("12400")),
                    new W2SocialSecurity(UUID.randomUUID(), "B", new BigDecimal("10000"), new BigDecimal("620")));

            StrategyResult result = strategy.evaluate(profile, Y2026);

            assertThat(result.outcome()).isEqualTo(Outcome.NOT_APPLICABLE);
            assertThat(result.explanation()).contains("Overwithholding Co alone withheld").contains("ask the employer");
        }
    }

    @Nested
    class WholeEngine {

        private final StrategyEngine engine = new StrategyEngine(List.of(
                new SCorpElection(StrategyAssumptionsYaml.sCorp()),
                new SelfEmployedRetirement(ASSUMPTIONS),
                new WorkplaceRetirement(ASSUMPTIONS),
                new HsaContribution(ASSUMPTIONS),
                new ExcessSocialSecurity()));

        @ParameterizedTest(name = "control {0} gets no recommendations")
        @ValueSource(strings = {"client-04", "client-08"})
        void controlClientsGetNoRecommendations(String client) {
            List<StrategyResult> results = engine.evaluate(profile(client), Y2026);

            assertThat(results).hasSize(5);
            assertThat(results).noneMatch(r -> r.outcome() == Outcome.RECOMMENDED);
        }

        @Test
        void client01GetsTheSpecsTwoW2Recommendations() {
            assertThat(recommended("client-01")).containsExactly(WorkplaceRetirement.CODE, HsaContribution.CODE);
        }

        @Test
        void client02GetsSCorpAndSolo401k() {
            // Week 2 done-criteria: client-02 sees an S-corp election and a Solo 401(k) recommendation.
            assertThat(recommended("client-02"))
                    .containsExactlyInAnyOrder(SCorpElection.CODE, SelfEmployedRetirement.CODE);
        }

        @Test
        void client10GetsTheExcessSocialSecurityCredit() {
            assertThat(recommended("client-10")).contains(ExcessSocialSecurity.CODE);
        }

        @Test
        void everyResultCarriesTheDisclaimer() {
            for (String client : Set.of("client-01", "client-02", "client-03", "client-09", "client-10")) {
                assertThat(engine.evaluate(profile(client), Y2026))
                        .allSatisfy(r -> assertThat(r.disclaimer()).isEqualTo(StrategyResult.DISCLAIMER));
            }
        }

        private List<String> recommended(String client) {
            return engine.evaluate(profile(client), Y2026).stream()
                    .filter(r -> r.outcome() == Outcome.RECOMMENDED)
                    .map(StrategyResult::code)
                    .toList();
        }
    }

    // --- helpers ---------------------------------------------------------------------------

    private static TaxProfile profile(String client) {
        return TaxProfileBuilder.build(ExtractionFixtures.documents(client), 2026);
    }

    /** One W-2 with wages = pay - deferrals - HSA, as box 1 would show. */
    private static TaxProfile w2Profile(String pay, String deferrals, String hsa) {
        BigDecimal gross = new BigDecimal(pay);
        BigDecimal ss = gross.subtract(new BigDecimal(hsa));
        return new TaxProfile("synthetic", 2026, ss.subtract(new BigDecimal(deferrals)),
                List.of(new W2SocialSecurity(UUID.randomUUID(), "Example Employer", ss,
                        ss.multiply(new BigDecimal("0.062")))),
                ss, new BigDecimal(deferrals), new BigDecimal(hsa), BigDecimal.ZERO, new java.util.TreeSet<>(),
                false, List.of(), List.of(), List.of());
    }

    private static TaxProfile profileWithW2s(W2SocialSecurity... w2s) {
        BigDecimal wages = List.of(w2s).stream().map(W2SocialSecurity::wages).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TaxProfile("synthetic", 2026, wages, List.of(w2s), wages, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new java.util.TreeSet<>(), false, List.of(), List.of(), List.of());
    }
}
