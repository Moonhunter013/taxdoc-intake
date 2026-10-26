# TaxDoc Intake & Strategy Finder

A demo project: upload tax documents (W-2, 1099-NEC), extract fields with AWS Textract,
review them, and get tax-planning opportunities with estimated dollar impact, plus an
LLM-written advisor summary and an MCP server so agents can work a client file.

> All data in this repo is **synthetic**. No real taxpayer information is used.
> Strategy estimates are illustrative, not tax advice.

## Architecture (target)

```
Angular 21 ──► extraction-service (FastAPI) ──► S3 + Textract
     │                    │
     └──► planning-service (Spring Boot, Java 21) ──► Claude API (advisor summary)
                          │
                       MySQL 8 ◄── mcp-server (agent tools)
```

| Folder | What | Status |
|---|---|---|
| `frontend/` | Angular 21 UI: upload, review and correct, plan results | Week 2 |
| `extraction-service/` | FastAPI: upload → S3 → Textract → validated fields → MySQL; per-client listing, field corrections | Weeks 1–2 |
| `planning-service/` | Spring Boot: five strategies (S-corp, Solo 401(k), workplace 401(k), HSA, excess Social Security) + Plans API | Week 2 |
| `mcp-server/` | MCP tools for agents | Week 3 |
| `legacy/` | Jersey 2.x service to migrate to Java 21 / Spring Boot | Week 4 |
| `samples/` | Synthetic client data and filled IRS forms | Week 0 |
| `infra/aws/` | Budget alarm, IAM policy, bucket setup | Week 0 |
| `docs/adr/` | Architecture decision records ([0003 service split](docs/adr/0003-service-boundaries.md), [0004 review rules](docs/adr/0004-extraction-confidence-and-review.md), [0005 sensitive data](docs/adr/0005-sensitive-data-handling.md), [0006 client identity](docs/adr/0006-client-identity.md), [0007 strategy engine](docs/adr/0007-strategy-engine.md)) | ongoing |
| `docs/planning/` | Issue specs per week ([Week 1](docs/planning/week-1/), [Week 2](docs/planning/week-2/)) | ongoing |

## Try it

With the stack running (below), open http://localhost:4200, enter a client reference such as
`client-02`, and:

1. **Upload** the client's forms from `samples/forms/` (e.g. `client-02_1099nec_1.pdf` and `_2.pdf`).
   Each file shows *Ready* or *Needs review*; uploading another person's form under the same client
   shows a misfiling warning.
2. **Review**: open a document to check each box against the PDF and correct values. Flags appear
   or clear immediately, because extraction re-runs its review rules on every correction.
3. **Plan**: *Generate plan*. For client-02 that's an S-corp election and a Solo 401(k),
   $13,316.67 a year in total, each with its explanation and assumptions.

Uploads call AWS S3 and Textract with your `taxdoc` profile (a few cents per form). Planning makes no
AWS calls.

## Running locally

Prerequisites: Docker Desktop, Java 21 (the Maven wrapper is included), Node 22 + npm 11, Python 3.13
with [uv](https://docs.astral.sh/uv/), and an AWS profile for uploads ([`infra/aws/`](infra/aws/README.md)).

```bash
cp .env.example .env          # set passwords and EXTRACTION_TIN_HMAC_KEY
docker compose up --build     # MySQL + both backend services (migrations run on start)
                              # uploads use your `taxdoc` AWS profile (~/.aws, mounted read-only)
cd frontend && npm install && npm start   # http://localhost:4200
```

`npm start` proxies `/api/v1/documents` and `/api/v1/clients` to extraction (:8000) and
`/api/v1/plans` to planning (:8080), so the services need no CORS setup (`frontend/proxy.conf.json`).

Health checks and API docs:
- extraction-service: http://localhost:8000/health (OpenAPI at `/docs`)
- planning-service: http://localhost:8080/actuator/health (OpenAPI at `/swagger-ui/index.html`)

Running the extraction service outside Docker (MySQL from compose must be up):

```bash
cd extraction-service
uv run alembic upgrade head
uv run uvicorn app.main:app --reload
```

Tests (the MySQL tests need Docker running; they are skipped otherwise):

```bash
cd extraction-service && uv sync && uv run pytest
cd planning-service && ./mvnw verify      # .\mvnw verify in PowerShell
cd frontend && npx ng test --watch=false
```

## How I work on this repo
- Small PRs tracked on a GitHub Projects Kanban board.
- Decisions recorded in [`docs/adr/`](docs/adr/).
- AI-assisted work, and what I changed or rejected, logged in [`AI_LOG.md`](AI_LOG.md).
