## Why
Textract returns a graph of blocks, not a W-2. This is the core translation layer.

## What
1. Generic parser: Textract response -> list of `(label, value, confidence)` key-value pairs
   (resolve KEY_VALUE_SET blocks, CHILD/VALUE relationships, WORD text)
2. W-2 mapper: match labels to W-2 fields (e.g. "1 Wages, tips, other compensation" -> box 1)
3. Value normalizers: `"$1,234.00"` -> `Decimal("1234.00")`, EIN/SSN cleanup, checkbox SELECTED state

## Acceptance criteria
- [ ] Pure functions, no AWS calls; tested against the saved fixtures (fixtures issue)
- [ ] Label matching tolerant of small OCR differences (case, punctuation, box-number prefixes)
- [ ] Unmatched or missing fields reported explicitly, not silently dropped
- [ ] `client-10_w2_*.pdf` (two W-2s) and `client-06_w2.pdf` (no state rows) parse correctly
