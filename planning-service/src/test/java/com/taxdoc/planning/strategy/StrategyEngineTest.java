package com.taxdoc.planning.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taxdoc.planning.extraction.ExtractionFixtures;
import com.taxdoc.planning.profile.TaxProfileBuilder;
import com.taxdoc.planning.rules.TaxYearsYaml;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyEngineTest {

    @Test
    void runsEveryStrategy() {
        StrategyEngine engine = new StrategyEngine(List.of(new SCorpElection(StrategyAssumptionsYaml.sCorp())));

        List<StrategyResult> results = engine.evaluate(
                TaxProfileBuilder.build(ExtractionFixtures.documents("client-02"), 2026),
                TaxYearsYaml.load().forYear(2026));

        assertThat(results).extracting(StrategyResult::code).containsExactly(SCorpElection.CODE);
    }

    @Test
    void ordersRecommendedThenMarginalThenNotApplicableAndBySavings() {
        List<StrategyResult> results = new ArrayList<>(List.of(
                StrategyResult.notApplicable("D", "d", "no", List.of()),
                StrategyResult.marginal("C", "c", new BigDecimal("900"), "small", List.of()),
                StrategyResult.recommended("B", "b", new BigDecimal("6000"), "yes", List.of()),
                StrategyResult.recommended("A", "a", new BigDecimal("9000"), "yes", List.of())));

        results.sort(StrategyEngine.ORDER);

        assertThat(results).extracting(StrategyResult::code).containsExactly("A", "B", "C", "D");
    }

    @Test
    void resultsCannotDropTheDisclaimer() {
        assertThatThrownBy(() -> new StrategyResult(
                        "X", "x", Outcome.RECOMMENDED, BigDecimal.TEN, "e", List.of(), "Trust me."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disclaimer");
    }

    @Test
    void notApplicableCannotClaimSavings() {
        assertThatThrownBy(() -> new StrategyResult("X", "x", Outcome.NOT_APPLICABLE, BigDecimal.TEN, "e",
                        List.of(), StrategyResult.DISCLAIMER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can't claim savings");
    }

    @Test
    void invalidAssumptionIsRejectedWithItsName() {
        var props = StrategyAssumptionsYaml.properties();
        assertThat(props.put("taxdoc.strategies.s-corp.salary-ratio", "60")).as("existing key").isNotNull();

        assertThatThrownBy(() -> StrategyAssumptionsYaml.sCorp(props))
                .rootCause()
                .hasMessageContaining("taxdoc.strategies.s-corp.salary-ratio must be a fraction");
    }
}
