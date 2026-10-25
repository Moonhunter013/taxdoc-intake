# ADR-0006: Client identity (advisor-assigned client_ref)

- **Status:** Accepted
- **Date:** 2026-10-25

## Context
Planning works per client (ADR-0003), so extraction must answer "which documents belong to this
client?". Two candidate keys exist: the taxpayer's SSN hash (ADR-0005) and an identifier the
advisor assigns.

## Options considered
1. **Use the taxpayer TIN hash as the client key:** automatic, no extra input. But a client
   engagement is not always one SSN: married couples file jointly (two SSNs), and a business
   owner's planning may include an EIN. The hash would also become a public identifier in URLs,
   and it changes if the HMAC key is rotated.
2. **Advisor-assigned `client_ref`, with the TIN hash as a consistency check.** *(chosen)*

## Decision
- Uploads accept an optional `client_ref` (letters, digits, `-`, `_`; max 64; validated on both
  the form field and the URL path). Documents are listed per client via
  `GET /api/v1/clients/{client_ref}/documents`.
- The TIN hash groups a client's documents by taxpayer. More than one group produces a warning
  ("check for documents filed under the wrong client"). Only document IDs are returned in the
  groups, never hashes or numbers.
- An unknown client returns an empty list, not 404: a new client simply has no documents yet.

## Consequences
- Joint filers will show two taxpayer groups and a warning. That's correct behaviour for now
  (it prompts a human check); a later "household" concept could mark expected spouses.
- `client_ref` is not authenticated yet: any caller can list any client. Authentication and
  per-advisor access control are required before real data (noted for Week 4 deployment).
- Existing documents without a `client_ref` remain reachable by ID only.
