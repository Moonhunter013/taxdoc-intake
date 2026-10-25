## Why
The UI and (in Week 3) the MCP server need one call that produces a client's plan.

## What
- `POST /api/v1/plans` `{ "clientRef": "client-02", "taxYear": 2026 }` -> runs every strategy,
  stores the plan, returns results sorted by estimated savings
- `GET /api/v1/plans/{id}`
- Bean Validation on requests, consistent error format (RFC 9457 problem details)

## Acceptance criteria
- [ ] OpenAPI docs (springdoc) for both endpoints
- [ ] Integration test: WireMock extraction + Testcontainers MySQL, end to end
- [ ] A plan built on documents that need review says so prominently
