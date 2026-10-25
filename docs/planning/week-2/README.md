# Week 2: Planning engine and UI

Goal: for a client whose documents are uploaded and reviewed, generate a plan of tax-saving
opportunities with estimated dollar impact, and show the whole flow in the browser.

Suggested order:

| # | Issue | Service | Depends on |
|---|---|---|---|
| 1 | Clients + document listing | extraction | - |
| 2 | Planning persistence (own schema, Flyway) | planning | - |
| 3 | Tax-year config (verified 2026 values) | planning | - |
| 4 | Extraction client + TaxProfile | planning | 1, 3 |
| 5 | Strategy engine + S-corp election | planning | 4 |
| 6 | Retirement, HSA, excess SS strategies | planning | 5 |
| 7 | Plans API | planning | 2, 6 |
| 8 | UI: upload | frontend | 1 |
| 9 | UI: review and correct | frontend | 8 |
| 10 | UI: plan results | frontend | 7, 9 |

Create the issues with `create-issues.ps1`.

**Done when:** upload client-02's two 1099-NECs in the browser, review them, generate a plan,
and see an S-corp election and Solo 401(k) recommendation with estimated savings, all covered by
tests in CI.

> All strategy output is an illustrative estimate on synthetic data, not tax advice.
