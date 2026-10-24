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
| `frontend/` | Angular 21 UI | Week 0 skeleton |
| `extraction-service/` | FastAPI: upload → S3 → Textract → validated fields → MySQL | Week 1 |
| `planning-service/` | Spring Boot: strategy rules engine + API | Week 0 skeleton |
| `mcp-server/` | MCP tools for agents | Week 3 |
| `legacy/` | Jersey 2.x service to migrate to Java 21 / Spring Boot | Week 4 |
| `samples/` | Synthetic client data and filled IRS forms | Week 0 |
| `infra/aws/` | Budget alarm, IAM policy, bucket setup | Week 0 |
| `docs/adr/` | Architecture decision records ([0004 review rules](docs/adr/0004-extraction-confidence-and-review.md), [0005 sensitive data](docs/adr/0005-sensitive-data-handling.md)) | ongoing |

## Running locally

Prerequisites: Docker Desktop, Java 21 + Maven, Node 22 + npm 11, Python 3.12+ with [uv](https://docs.astral.sh/uv/).

```bash
cp .env.example .env          # set passwords and EXTRACTION_TIN_HMAC_KEY
docker compose up --build     # MySQL + both backend services (migrations run on start)
cd frontend && npm install && npm start   # http://localhost:4200
```

Health checks:
- extraction-service: http://localhost:8000/health (API docs at `/docs`)
- planning-service: http://localhost:8080/actuator/health and `/api/v1/status`

Running the extraction service outside Docker (MySQL from compose must be up):

```bash
cd extraction-service
uv run alembic upgrade head
uv run uvicorn app.main:app --reload
```

Tests (the MySQL tests need Docker running; they are skipped otherwise):

```bash
cd extraction-service && uv sync && uv run pytest
cd planning-service && mvn verify
cd frontend && npx ng test --watch=false
```

## How I work on this repo
- Small PRs tracked on a GitHub Projects Kanban board.
- Decisions recorded in [`docs/adr/`](docs/adr/).
- AI-assisted work, and what I changed or rejected, logged in [`AI_LOG.md`](AI_LOG.md).
