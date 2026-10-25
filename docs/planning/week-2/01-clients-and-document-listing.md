## Why
Planning works per client, but documents are currently only reachable one at a time by ID.
ADR-0003 says planning reads documents through extraction's API, so extraction needs a
"documents for a client" endpoint.

## What
- Add a `client_ref` (advisor-chosen client identifier, e.g. `client-10`) to uploads: an
  optional form field on `POST /api/v1/documents`, stored on `documents`, indexed. New Alembic
  migration.
- `GET /api/v1/clients/{client_ref}/documents` returns that client's documents (same shape as
  `GET /api/v1/documents/{id}`), newest first.
- When a client's documents carry **different** taxpayer TIN hashes (e.g. a spouse's W-2
  uploaded to the wrong client), add a review reason, without revealing either number.

## Acceptance criteria
- [ ] Migration `0002` adds `client_ref` (nullable for existing rows) with an index
- [ ] Unknown client returns an empty list, not 404 (a new client simply has no documents yet)
- [ ] Mismatched taxpayer hashes within one client are flagged in the response
- [ ] Repository + API tests (in-memory and MySQL)
- [ ] ADR-0006: why an advisor-assigned `client_ref` rather than the SSN hash as the client key
