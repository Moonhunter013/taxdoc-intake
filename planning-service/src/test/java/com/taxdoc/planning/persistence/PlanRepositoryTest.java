package com.taxdoc.planning.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taxdoc.planning.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

// Repeated from the base class so JUnit skips cleanly without Docker on every version.
@EnabledIf("com.taxdoc.planning.MySqlIntegrationTest#dockerAvailable")
class PlanRepositoryTest extends MySqlIntegrationTest {

    @Autowired
    PlanRepository plans;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        plans.deleteAll();
    }

    private static Plan samplePlan() {
        Plan plan = new Plan("client-02", 2026, false, LocalDateTime.of(2026, 10, 25, 12, 0));
        plan.addResult(new StrategyResultEntity(
                "S_CORP_ELECTION", true, new BigDecimal("9180.25"),
                "Paying a reasonable salary reduces self-employment tax.",
                List.of("Reasonable salary of 60% of net profit", "Extra compliance cost of $2,000/yr")));
        plan.addResult(new StrategyResultEntity(
                "HSA", false, BigDecimal.ZERO, "No W-2 income, so no employer HSA.", List.of()));
        return plan;
    }

    @Test
    void flywayCreatedTheSchemaAndHibernateValidatedIt() {
        // The context only starts if ddl-auto=validate accepts the Flyway schema.
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void savesAndReloadsAPlanWithExactMoney() {
        Plan saved = plans.save(samplePlan());

        Plan loaded = plans.findWithResultsById(saved.getId()).orElseThrow();
        assertThat(loaded.getClientRef()).isEqualTo("client-02");
        assertThat(loaded.getTotalEstimatedSavings()).isEqualByComparingTo("9180.25"); // only applicable
        assertThat(loaded.getResults()).hasSize(2);
        StrategyResultEntity first = loaded.getResults().get(0); // ordered by savings, highest first
        assertThat(first.getStrategyCode()).isEqualTo("S_CORP_ELECTION");
        assertThat(first.getEstimatedSavings()).isEqualTo(new BigDecimal("9180.25")); // scale kept
        assertThat(first.getAssumptions()).containsExactly(
                "Reasonable salary of 60% of net profit", "Extra compliance cost of $2,000/yr");
    }

    @Test
    void deletingAPlanDeletesItsResults() {
        Plan saved = plans.save(samplePlan());
        plans.deleteById(saved.getId());
        Integer remaining = jdbc.queryForObject("SELECT COUNT(*) FROM strategy_results", Integer.class);
        assertThat(remaining).isZero();
    }

    @Test
    void findsPlansForAClientNewestFirst() {
        Plan older = plans.save(samplePlan());
        Plan newer = plans.save(new Plan("client-02", 2026, true, LocalDateTime.of(2026, 10, 26, 9, 0)));
        assertThat(plans.findByClientRefOrderByCreatedAtDesc("client-02"))
                .extracting(Plan::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void planningUserCannotReadExtractionTables() {
        // ADR-0003: each service owns its data. The app connects as 'planning'.
        // Spring wraps MySQL's error (as BadSqlGrammarException), so check the root cause.
        assertThatThrownBy(() -> jdbc.queryForList("SHOW TABLES FROM taxdoc"))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("Access denied for user 'planning'")
                .hasMessageContaining("'taxdoc'");
    }
}
