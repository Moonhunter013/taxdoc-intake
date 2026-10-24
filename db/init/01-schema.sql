-- Runs once, the first time the MySQL container starts with an empty volume.
-- Real tables arrive with migrations in Week 1 (Alembic) and Week 2 (Flyway).
CREATE TABLE IF NOT EXISTS schema_bootstrap (
    id INT PRIMARY KEY,
    note VARCHAR(255) NOT NULL
);
INSERT IGNORE INTO schema_bootstrap (id, note) VALUES (1, 'week 0 bootstrap');
