## Why
Strategy maths depends on limits that change every year (Social Security wage base, retirement
contribution limits, HSA limits, standard deduction...). Hard-coding them is the classic bug
that silently breaks every January.

## What
- `@ConfigurationProperties` class `TaxYearRules` loaded from `application.yml`, keyed by year
- 2026 values for everything Week 2 strategies need, each with a comment linking the IRS/SSA
  source it came from
- Fail fast at startup if a configured year is missing a required value

## Acceptance criteria
- [ ] Every 2026 value is checked against a primary source (IRS notice/newsroom or SSA), with
      the link in the YAML comment
- [ ] Requesting a plan for an unconfigured year returns a clear 422, not a wrong answer
- [ ] Unit tests for loading and validation
