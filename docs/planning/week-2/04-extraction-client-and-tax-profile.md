## Why
The strategy engine should work on one clean picture of the client, not on raw W-2/1099 JSON.

## What
- `ExtractionClient` (Spring `RestClient`) that calls `GET /api/v1/clients/{ref}/documents`
  with timeouts and clear errors when extraction is down
- `TaxProfile` (Java records, `BigDecimal`): total W-2 wages, Social Security wages and tax per
  employer, Medicare wages, 401(k)/HSA deferrals from box 12, total 1099-NEC income, state(s),
  and whether any source document still `needs_review`

## Acceptance criteria
- [ ] Money parsed from decimal strings into `BigDecimal`; no `double` anywhere
- [ ] Aggregation tests using the sample clients (client-03: W-2 + 1099; client-10: two W-2s)
- [ ] WireMock (or MockRestServiceServer) tests for timeouts, 5xx and malformed responses
- [ ] Profiles built from documents that need review are marked provisional
