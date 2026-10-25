## Why
Human review is the core of the design (ADR-0004). The advisor must see what needs checking and
fix it quickly.

## What
- Route `/clients/:clientRef/documents/:id`: the extracted fields grouped by form box, with
  flagged fields highlighted and their `review_reasons` shown
- Inline edit that calls `PATCH .../fields/{field}` and re-renders with the updated review state
- SSNs/EINs only ever displayed masked

## Acceptance criteria
- [ ] Editing box 4 to an inconsistent value shows the new flag immediately (round-trips the API)
- [ ] Validation errors from the API shown next to the field
- [ ] Component tests with a mocked API service
