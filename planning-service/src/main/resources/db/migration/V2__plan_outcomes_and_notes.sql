-- Plans API (#24): store the strategy outcome (ADR-0007) instead of a yes/no "applicable",
-- each result's title, and the plan-level notes GET must return exactly as POST did.

-- 1. Results: outcome replaces applicable. Existing rows are mapped, not dropped:
--    applicable = true -> RECOMMENDED, false -> NOT_APPLICABLE (MARGINAL didn't exist before).
ALTER TABLE strategy_results
    ADD COLUMN outcome VARCHAR(16)  NULL AFTER strategy_code,
    ADD COLUMN title   VARCHAR(200) NULL AFTER outcome;

UPDATE strategy_results
SET outcome = CASE WHEN applicable THEN 'RECOMMENDED' ELSE 'NOT_APPLICABLE' END,
    title   = strategy_code;

ALTER TABLE strategy_results
    MODIFY COLUMN outcome VARCHAR(16)  NOT NULL,
    MODIFY COLUMN title   VARCHAR(200) NOT NULL,
    ADD CONSTRAINT ck_strategy_results_outcome
        CHECK (outcome IN ('RECOMMENDED', 'MARGINAL', 'NOT_APPLICABLE')),
    DROP COLUMN applicable;

-- 2. Plans: extraction warnings and the documents that made the plan provisional.
--    Newline-separated like strategy assumptions; empty for plans made before this migration.
ALTER TABLE plans
    ADD COLUMN warnings                 VARCHAR(4000) NOT NULL DEFAULT '',
    ADD COLUMN documents_needing_review VARCHAR(2000) NOT NULL DEFAULT '';
