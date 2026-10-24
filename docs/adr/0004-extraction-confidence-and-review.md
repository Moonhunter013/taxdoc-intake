# ADR-0004: Extraction confidence thresholds and human review

- **Status:** Accepted
- **Date:** 2026-10-24

## Context
Extracted values feed tax-planning estimates, so a misread number must reach a person before it
is used. Textract returns a confidence score (0-100) per field, and we can also check arithmetic
that must hold on a genuine form.

Measured on all 15 sample forms, where **every field was extracted correctly** (169/169):

| Fields | Min confidence | Avg |
|---|---|---|
| W-2 state row (box 15 state & ID, 16, 17) | 72.4 | ~77 |
| 1099-NEC state & ID (box 6) | 76.1 | 76.1 |
| Employee name | 86.1 | 89.8 |
| SSN, box 7 state income | 93.4 | ~94 |
| All other money, name, address and TIN fields | 94.2 | ~95 |
| Box 12 (read from text lines, not key-value pairs) | 99.7 | 99.8 |

Textract's confidence is clearly not uniform across the page: correct state-row values score
20 points lower than correct values elsewhere.

## Options considered
1. **Single 90% threshold:** simple, but flags every form with a state row although all were
   correct. Reviewers learn to ignore flags, which defeats the purpose.
2. **Single 70% threshold:** few flags, but a genuinely misread wage box at 85% would pass.
3. **Flag only when low confidence AND a consistency rule fails:** fewest false alarms, but
   harder to explain and fields without a rule could never be flagged.
4. **Per-field thresholds plus independent consistency rules.** *(chosen)*

## Decision
- Default threshold **90%**. State-row fields **70%**. Employee name **80%** (display only;
  clients are matched by SSN, which keeps 90%). All configurable in `app/config.py` and
  overridable through environment variables, so they can be retuned without code changes.
- Consistency rules always run, regardless of confidence, because Textract can be confidently
  wrong. W-2: box 4 = 6.2% of box 3 (+/- $1); box 3 <= the tax year's wage base; box 6 >= 1.45%
  of box 5; box 1 <= box 5; box 12 codes are known. 1099-NEC: boxes 4 and 7 <= box 1a.
- Review never changes a value. It sets `needs_review` per field and explains why in
  `review_reasons`; a person makes the correction.
- A calibration test asserts that none of the 15 correct sample forms is flagged, so a future
  threshold change that would cause false alarms fails CI.

## Consequences
- Thresholds are calibrated on 15 synthetic forms from one generator. Real scans (skewed,
  handwritten corrections, different software) will score differently; recalibrate once real
  documents are available, and treat these numbers as a starting point.
- A misread state value between 70% and its true confidence would not be flagged by confidence
  alone; the state-tax consistency check is weak (rates vary by state), so state values rely
  more on reviewer spot checks.
- Adding a tax year requires adding its wage base to config; until then every W-2 for that year
  is flagged, which fails safe.
