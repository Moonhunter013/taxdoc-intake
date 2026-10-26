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
| `frontend/` | Angular 21, Vitest, Tailwind 4, npm 11 (pinned via `packageManager`) | Upload, review, plan screens; `proxy.conf.json` routes `/api/v1/*` to both services |
| `db/init/` | MySQL init scripts | `02-planning-db.sh` creates the `planning` schema/user |
| `samples/` | `clients.json`, filled IRS forms, `ground-truth/`, `textract-fixtures/` | 10 synthetic clients |
| `docs/adr/` | ADR-0001 to 0007 | 0007: strategy engine and how estimates are communicated |
| `docs/planning/` | `week-1/`, `week-2/` issue specs (week-2 `01`–`10` = GitHub #18–#27) | Read the spec before starting an issue |
| `planning-service/scripts/` | `capture_extraction_fixtures.py` | Regenerates planning's test fixtures from extraction's real app |
| `legacy/`, `mcp-server/`, `infra/aws/` | Week 3–4 | |

## Commands (PowerShell, from repo root)
```powershell
docker compose up --build -d                 # MySQL + both services; migrations run on start
docker compose down -v                       # wipe the DB volume (re-runs db/init scripts)
cd planning-service; .\mvnw verify; cd ..    # Java tests (DB tests need Docker running)
cd extraction-service; uv sync; uv run pytest; uv run ruff check .; uv run ruff format --check .; cd ..
cd frontend; npm ci; npx ng test --watch=false; npx ng build; cd ..
cd frontend; npm start                       # http://localhost:4200, proxies to :8000 and :8080
```
Health: http://localhost:8000/health, http://localhost:8080/actuator/health. API docs: :8000/docs,
:8080/swagger-ui/index.html. CI runs exactly the commands above (including `ruff format --check`).

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
- **Boot 4 uses Jackson 3** (`tools.jackson.*`); springdoc's swagger-core also brings Jackson 2
  (`com.fasterxml.*`). They coexist. RestClient is built by hand (no `spring-boot-restclient`).
- **JDK HttpClient → uvicorn:** pin HTTP/1.1, or it offers an h2c upgrade on every request.
- **MySQL `DATETIME(6)`** rounds sub-microsecond values: truncate `createdAt` to micros or GET
  differs from POST.
- **Extraction container** runs as `appuser`; `~/.aws` is mounted read-only at `/home/appuser/.aws`
  via `${HOME:-${USERPROFILE}}` (PowerShell doesn't export HOME). Dev only; ECS uses a task role.
- **Windows tooling:** `mvnw` (Unix script) fails in Git Bash, use `.\mvnw.cmd`; .NET file APIs in
  PowerShell resolve relative paths from the process folder, so use absolute paths; piping
  PowerShell output into Python adds a BOM; the scratchpad path exceeds MAX_PATH for `curl -o`;
  Python `write_text` writes CRLF unless `newline="\n"`.
- **Angular:** upload progress needs HttpClient's default XHR backend (fetch has none); the
  `autofocus` attribute doesn't work on elements inserted later; a dark-mode browser paints a dark
  page unless `color-scheme: light` and body colours are set.
- **Tests that pass first time** get a deliberate break (mutation check) before handing over; three
  weak tests were found this way in Week 2.

## Domain decisions
- Review: per-field Textract confidence thresholds (90 default, 70 state row, 80 employee name)
  plus consistency rules that always run (ADR-0004).
- Clients are identified by an advisor-assigned `client_ref`, not the SSN hash (ADR-0006).
- Tax constants live in `planning-service/src/main/resources/tax-years.yml`, keyed by year, with a
  source link per value. `TaxRules.forYear()` throws `UnsupportedTaxYearException` (→ HTTP 422);
  no fallback to another year. Strategies must use `TaxYearRules`, never literals.
- `clients.json` has no filing status or age: strategies assume single and under 50, and must
  list that in their assumptions.
- Strategy judgement calls live in `planning-service/src/main/resources/strategy-assumptions.yml`,
  never in code (ADR-0007): S-corp salary 60% of profit capped at $120k, $2,500/yr costs,
  recommend from $5,000 net; contributions assume a 15% savings rate, recommend from $1,000.
  Outcomes are RECOMMENDED / MARGINAL / NOT_APPLICABLE; plan totals count RECOMMENDED only.
  Income tax effects like the QBI deduction are not modelled and every result says so.
- Money crosses every API as decimal strings; the UI formats them with `formatMoney` (no floats).
- Review reasons are per field (`fields[name].review_reasons`, Alembic 0003) as well as per document.
- Plans are generated only on request (every POST stores one); the plan id goes in the URL.

## Status (2026-10-26)
- Weeks 0–2 done. Week 2 (#18–#27) merged; done-criteria shown live: client-02 uploaded through
  real Textract, reviewed, planned (S-corp + Solo 401(k), $13,316.67).
- Dev database: client-02 has its two 1099-NECs and two plans; one 1099 has corrections recorded
  as "Claude (live check)" (values restored).
- **Next: Week 3.** No specs yet: write `docs/planning/week-3/` first (LLM advisor summary via the
  Claude API, MCP server for agents), then create the issues.

## Loose ends
- This docs branch (`docs/ai-log-and-claude-md`) holds AI_LOG rows for #18–#27 and this file;
  Paul pushes it and opens the PR.
- Box 12 codes TA/TP/TT (2026) are done but uncommitted in worktree branch
  `claude/inspiring-colden-f9c2e5`; run `uv run ruff format --check .` before its PR.
- Parked branch `docs/readme-screenshot` not pushed yet.
- Optional: Playwright smoke test (upload → review → plan); an extraction endpoint to delete or
  move misfiled documents (cleanup was manual SQL); Angular 22 upgrade; QBI modelling (ADR-0007).
- Optional TODO in `AI_LOG.md`: hand-check one W-2 against `samples/ground-truth/`.
