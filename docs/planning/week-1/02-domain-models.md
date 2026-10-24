## Why
Extraction output needs a strict, typed shape before anything downstream (DB, planning service, LLM) touches it.

## What
Pydantic models in `extraction-service/app/models/`:
- `W2`: employer (name, EIN, address), employee, boxes 1-6, box 12 entries (code + amount), box 13 retirement flag, state rows (15-17)
- `Form1099NEC`: payer, recipient, boxes 1a, 4, 5-7, calendar year
- `ExtractedField[T]`: value + Textract confidence + `needs_review` flag

## Acceptance criteria
- [ ] Money fields use `Decimal`, never `float`
- [ ] SSN/EIN/TIN format validation (masked in `__repr__`/logs)
- [ ] Negative amounts rejected; box 12 codes validated against the IRS code list
- [ ] Unit tests for valid and invalid examples
- [ ] Models match the keys in `samples/ground-truth/*.json` (or a documented mapping)
