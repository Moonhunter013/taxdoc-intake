# CLAUDE.md

Context for Claude Code on this repo. Read this first, then `README.md`, `AI_LOG.md` and the ADRs.

## What this is
A portfolio project for a Senior Full-Stack Engineer application (TaxPlanIQ). Upload W-2 / 1099-NEC
PDFs, extract fields with AWS Textract, review them, then generate tax-planning strategies with
estimated savings, an LLM advisor summary (Week 3) and an MCP server for agents (Week 3).
Week 4: migrate the legacy Jersey service and deploy to AWS (ECS, S3, CloudFront, Aurora MySQL).

The posting stresses **AI-assisted development with accountability**. That is the point of
`AI_LOG.md`: record what the AI got wrong and what the human caught or changed.

All data is synthetic. Strategy output is an illustrative estimate, not tax advice.

## Working agreement
- **I draft, Paul reviews.** Make PR-sized changes for one GitHub issue at a time and explain the
  key decisions. Paul reviews the diff, branches, commits, pushes and opens the PRs. Don't commit
  or push unless asked.
- Before writing code that depends on facts (tax limits, library APIs, versions), verify them
  against a primary source and cite it. Never use tax numbers from memory.
- Run the tests yourself before handing over (`mvnw verify`, `uv run pytest`, `ng test`). When a
  test fails, find the root cause; don't weaken the assertion or add a retry/wait.
- After each issue, propose an `AI_LOG.md` row (date | tool | task | kept | changed/rejected and why).
  Be honest about AI mistakes; they are the valuable part.
- Windows 11 + PowerShell. Give PowerShell syntax, not bash, in instructions for Paul.

## Hard rules
- Never commit `.env` (real secrets). `.env.example` holds placeholders only.
- Never add Paul's personal `CGexam2025*.pdf` files to git.
- Planning-service reads extraction-service's **API**, never its database (ADR-0003). Each
  service has its own MySQL schema and user.
- SSNs/EINs are never stored in full: last 4 + keyed HMAC-SHA256 (ADR-0005).
- Paul's editor can auto-save an open file over your changes. Ask him to close a file before
  you rewrite it, and re-check file contents after writing when something "didn't take".

## Layout
| Path | Stack | Notes |
|---|---|---|
| `extraction-service/` | Python 3.13, FastAPI, uv, Ruff, pytest, SQLAlchemy 2.1, Alembic, PyMySQL, boto3 | Upload → S3 → Textract → parse → review flags → MySQL |
| `planning-service/` | Java 21, Spring Boot 4.1.2, Maven wrapper, JPA, Flyway, Testcontainers 2 | Strategy engine + Plans API |
| `frontend/` | Angular 21, Vitest, npm 11 (pinned via `packageManager`) | Week 2 screens pending |
| `db/init/` | MySQL init scripts | `02-planning-db.sh` creates the `planning` schema/user |
| `samples/` | `clients.json`, filled IRS forms, `ground-truth/`, `textract-fixtures/` | 10 synthetic clients |
| `docs/adr/` | ADR-0001 to 0006 | Next: ADR-0007 strategy engine |
| `docs/planning/week-2/` | Issue specs `01`–`10` (= GitHub #18–#27) | Read the spec before starting an issue |
| `legacy/`, `mcp-server/`, `infra/aws/` | Week 3–4 | |

## Commands (PowerShell, from repo root)
```powershell
docker compose up --build -d                 # MySQL + both services; migrations run on start
docker compose down -v                       # wipe the DB volume (re-runs db/init scripts)
cd planning-service; .\mvnw verify; cd ..    # Java tests (DB tests need Docker running)
cd extraction-service; uv sync; uv run pytest; uv run ruff check .; cd ..
cd frontend; npm ci; npx ng test --watch=false; cd ..
```
Health: http://localhost:8000/health, http://localhost:8080/actuator/health

## Technical gotchas already hit (don't repeat)
- **Boot 4:** needs `spring-boot-starter-flyway` (with only `flyway-core`, Flyway silently doesn't
  run). Web starter is `spring-boot-starter-webmvc`; `@AutoConfigureMockMvc` is in
  `org.springframework.boot.webmvc.test.autoconfigure`.
- **Testcontainers 2:** artifact `testcontainers-mysql`, class `org.testcontainers.mysql.MySQLContainer`
  (not generic). `@ServiceConnection` is in `org.springframework.boot.testcontainers.service.connection`.
  DB tests extend `MySqlIntegrationTest` and are skipped without Docker.
- **MySQL init scripts:** files from Windows are executable, so the entrypoint *runs* `.sh` files
  instead of sourcing them. Helpers like `docker_process_sql` don't exist; call `mysql` directly.
- **Compose healthcheck** must ping over TCP (`-h 127.0.0.1 --protocol=tcp`). A socket ping passes
  while the init-time temporary server has networking off.
- **Spring YAML map keys** like `2026` may be flattened as `years[2026]`; tests that edit
  properties must match keys robustly (see `TaxYearsYaml.keyFor`).
- **Exceptions:** Spring wraps SQL errors; assert on `.rootCause()`.
- **npm audit:** don't run `--force` (it downgrades Angular); use `overrides` in package.json.
- **Angular CLI** analytics prompts can write into `angular.json`; restore with `git restore`.
- **uv** on Windows picked a too-new Python; `.python-version` pins 3.13.

## Domain decisions
- Review: per-field Textract confidence thresholds (90 default, 70 state row, 80 employee name)
  plus consistency rules that always run (ADR-0004).
- Clients are identified by an advisor-assigned `client_ref`, not the SSN hash (ADR-0006).
- Tax constants live in `planning-service/src/main/resources/tax-years.yml`, keyed by year, with a
  source link per value. `TaxRules.forYear()` throws `UnsupportedTaxYearException` (→ HTTP 422);
  no fallback to another year. Strategies must use `TaxYearRules`, never literals.
- `clients.json` has no filing status or age: strategies assume single and under 50, and must
  list that in their assumptions.

## Status (2026-10-25)
- Week 0 and Week 1 done (extraction: 169/169 fields correct across 15 forms).
- Week 2 merged: #18 client documents, #19 planning persistence, #20 tax-year config.
- **Next: #21** extraction client + `TaxProfile` (spec: `docs/planning/week-2/04-...`), then
  #22 strategy engine + S-corp (ADR-0007), #23 retirement/HSA/excess SS, #24 Plans API,
  #25–#27 Angular screens. Week 2 done-criteria are in `docs/planning/week-2/README.md`.

## Loose ends
- `AI_LOG.md` has uncommitted rows for #18–#20; Paul will push them on a docs branch.
- Parked branch `docs/readme-screenshot` not pushed yet; README also needs links to ADR-0006 and
  `docs/planning/`.
- Optional TODO in `AI_LOG.md`: hand-check one W-2 against `samples/ground-truth/`.
