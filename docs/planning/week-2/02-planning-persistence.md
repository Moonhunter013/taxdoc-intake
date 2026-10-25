## Why
ADR-0003: each service owns its data. Planning needs its own schema and database user so it can
never read or write extraction's tables.

## What
- Separate MySQL schema `planning` and user `planning` (compose init script + `.env.example`)
- Spring Data JPA + Flyway in planning-service; first migration creates `plans` and
  `strategy_results`
- Testcontainers MySQL for planning-service integration tests (same image as extraction: 8.4)

## Acceptance criteria
- [ ] The `planning` user has privileges on the `planning` schema only (verified by a test or a
      documented manual check)
- [ ] `mvnw verify` runs Flyway against a real MySQL container
- [ ] Money columns are `DECIMAL(12,2)`, mapped to `BigDecimal` (never `double`)
- [ ] docker compose starts planning-service with its own datasource settings
