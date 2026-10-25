-- Plans and their per-strategy results (#19).
-- Money is DECIMAL(12,2), mapped to BigDecimal; never FLOAT/DOUBLE.

CREATE TABLE plans (
    id                      VARCHAR(36)    NOT NULL PRIMARY KEY,
    client_ref              VARCHAR(64)    NOT NULL,
    tax_year                INT            NOT NULL,
    provisional             BOOLEAN        NOT NULL,
    total_estimated_savings DECIMAL(12, 2) NOT NULL,
    created_at              DATETIME(6)    NOT NULL,
    INDEX ix_plans_client_ref (client_ref)
);

CREATE TABLE strategy_results (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_id           VARCHAR(36)    NOT NULL,
    strategy_code     VARCHAR(64)    NOT NULL,
    applicable        BOOLEAN        NOT NULL,
    estimated_savings DECIMAL(12, 2) NOT NULL,
    explanation       VARCHAR(2000)  NOT NULL,
    assumptions       VARCHAR(4000)  NOT NULL,
    CONSTRAINT fk_strategy_results_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE,
    CONSTRAINT uq_strategy_results_plan_code UNIQUE (plan_id, strategy_code)
);
