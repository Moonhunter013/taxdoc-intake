## Why
"It works on my W-2" isn't evidence. Measure extraction accuracy against known answers.

## What
Parametrized pytest suite over all 15 fixtures comparing parsed output to `samples/ground-truth/`.

## Acceptance criteria
- [ ] One test case per document; failures name the field, expected and actual value
- [ ] A per-field accuracy summary printed at the end of the run (e.g. "box1: 15/15")
- [ ] Runs offline in CI (fixtures only)
- [ ] Any known OCR misses documented, not hidden with loose assertions
