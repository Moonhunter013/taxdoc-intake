## Why
OCR is probabilistic and tax numbers must be right. Low-confidence or inconsistent values should go
to a human, not straight into a tax plan.

## What
1. Per-field confidence threshold (configurable, start at 90) -> `needs_review = true`
2. Cross-field consistency checks on W-2s:
   - box 4 ~= box 3 x 6.2% (within $1)
   - box 3 <= Social Security wage base for the tax year (2026: $184,500, from config)
   - box 6 >= box 5 x 1.45%
   - box 1 <= box 5 + small tolerance (pre-tax deductions reduce box 1)
3. A `review_reasons` list on each document explaining why it was flagged

## Acceptance criteria
- [ ] Threshold and tax-year constants live in config, not code
- [ ] Unit tests: a clean document passes; tampered fixtures trigger each rule
- [ ] ADR explaining the threshold choice and the "human in the loop" approach (see the Week 1 ADR issue)
