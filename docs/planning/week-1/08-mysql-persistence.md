## Why
Extracted documents must survive restarts and be readable by the planning service later.

## What
SQLAlchemy 2.0 models + Alembic migrations for MySQL:
- `documents` (id, client_ref, form_type, tax_year, s3_key, status, created_at)
- `extracted_fields` (document_id, field_name, value, confidence, needs_review)
Plus `GET /api/v1/documents/{id}` and `PATCH` to correct a flagged field (records who/when).

## Acceptance criteria
- [ ] `alembic upgrade head` creates the schema on the compose MySQL
- [ ] Money stored as `DECIMAL(12,2)`
- [ ] SSNs/TINs stored masked or encrypted. Decide and record the choice in an ADR
- [ ] Integration test against a real MySQL (Testcontainers or compose service in CI)
- [ ] Remove the `schema_bootstrap` placeholder table from `db/init`
