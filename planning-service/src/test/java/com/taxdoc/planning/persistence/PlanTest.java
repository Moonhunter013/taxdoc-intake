package com.taxdoc.planning.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.taxdoc.planning.strategy.Outcome;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The plan total, without a database. */
class PlanTest {

    @Test
    void totalCountsRecommendedResultsOnly() {
        Plan plan = new Plan("client-07", 2026, false, LocalDateTime.of(2026, 10, 26, 12, 0));
        plan.addResult(result("SOLO_401K", Outcome.RECOMMENDED, "3135.00"));
        plan.addResult(result("S_CORP_ELECTION", Outcome.MARGINAL, "2202.07"));
        plan.addResult(result("HSA", Outcome.NOT_APPLICABLE, "0"));

        assertThat(plan.getTotalEstimatedSavings()).isEqualTo(new BigDecimal("3135.00"));
    }

    @Test
    void totalIsZeroWithNoRecommendations() {
        Plan plan = new Plan("client-04", 2026, false, LocalDateTime.of(2026, 10, 26, 12, 0));
        plan.addResult(result("WORKPLACE_401K", Outcome.MARGINAL, "354.00"));

        assertThat(plan.getTotalEstimatedSavings()).isEqualTo(new BigDecimal("0.00"));
    }

    private static StrategyResultEntity result(String code, Outcome outcome, String savings) {
        return new StrategyResultEntity(code, outcome, code, new BigDecimal(savings), "e", List.of());
    }
}
