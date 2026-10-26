package com.taxdoc.planning.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taxdoc.planning.MySqlIntegrationTest;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V2 must convert rows written under V1, not just work on an empty database: a developer's
 * docker volume (or a deployed database) already has V1 data.
 *
 * <p>Uses its own throwaway schema, created as the container's root user, so it can stop Flyway
 * at V1 without touching the application's schema.
 */
// Repeated from the base class so JUnit skips cleanly without Docker on every version.
@EnabledIf("com.taxdoc.planning.MySqlIntegrationTest#dockerAvailable")
class MigrationV2Test extends MySqlIntegrationTest {

    private static final String SCHEMA = "planning_migration_test";

    private JdbcTemplate root;
    private DataSource schemaDataSource;

    @BeforeEach
    void freshSchema() {
        root = new JdbcTemplate(dataSource("mysql"));
        root.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        root.execute("CREATE DATABASE " + SCHEMA);
        schemaDataSource = dataSource(SCHEMA);
    }

    @AfterEach
    void dropSchema() {
        root.execute("DROP DATABASE IF EXISTS " + SCHEMA);
    }

    @Test
    void convertsV1ResultsAndPlans() {
        flyway("1").migrate();
        JdbcTemplate db = new JdbcTemplate(schemaDataSource);
        db.update("INSERT INTO plans (id, client_ref, tax_year, provisional, total_estimated_savings, created_at) "
                + "VALUES ('p1', 'client-02', 2026, false, 9180.25, '2026-10-25 12:00:00')");
        db.update("INSERT INTO strategy_results (plan_id, strategy_code, applicable, estimated_savings, explanation, "
                + "assumptions) VALUES ('p1', 'S_CORP_ELECTION', true, 9180.25, 'Recommended.', 'a')");
        db.update("INSERT INTO strategy_results (plan_id, strategy_code, applicable, estimated_savings, explanation, "
                + "assumptions) VALUES ('p1', 'HSA', false, 0, 'No W-2.', '')");

        flyway("2").migrate();

        List<Map<String, Object>> results = db.queryForList(
                "SELECT strategy_code, outcome, title FROM strategy_results ORDER BY strategy_code");
        assertThat(results).containsExactly(
                Map.of("strategy_code", "HSA", "outcome", "NOT_APPLICABLE", "title", "HSA"),
                Map.of("strategy_code", "S_CORP_ELECTION", "outcome", "RECOMMENDED", "title", "S_CORP_ELECTION"));
        assertThat(db.queryForMap("SELECT warnings, documents_needing_review FROM plans WHERE id = 'p1'"))
                .containsEntry("warnings", "").containsEntry("documents_needing_review", "");
        assertThat(db.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                                + "AND table_name = 'strategy_results' AND column_name = 'applicable'",
                        Integer.class, SCHEMA))
                .as("applicable column dropped")
                .isZero();
    }

    @Test
    void outcomeIsConstrainedToKnownValues() {
        flyway("2").migrate();
        JdbcTemplate db = new JdbcTemplate(schemaDataSource);
        db.update("INSERT INTO plans (id, client_ref, tax_year, provisional, total_estimated_savings, created_at) "
                + "VALUES ('p1', 'client-02', 2026, false, 0, '2026-10-25 12:00:00')");

        assertThatThrownBy(() -> db.update("INSERT INTO strategy_results (plan_id, strategy_code, outcome, title, "
                        + "estimated_savings, explanation, assumptions) VALUES ('p1', 'X', 'MAYBE', 't', 0, 'e', '')"))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("ck_strategy_results_outcome");
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(schemaDataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static DataSource dataSource(String schema) {
        return new DriverManagerDataSource(
                "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + schema, "root",
                MYSQL.getPassword());
    }
}
