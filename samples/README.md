# Synthetic test data

**Everything here is fictional.** SSNs use area `000` and EINs use prefix `00`, which are never
issued. Every generated PDF is stamped "SYNTHETIC TEST DATA".

| Path | What |
|---|---|
| `clients.json` | 10 fictional clients (tax year 2026), each built around a planning scenario |
| `blank-forms/` | Official blank IRS W-2 and 1099-NEC PDFs (downloaded from irs.gov) |
| `generate_forms.py` | Fills the blank forms from `clients.json` |
| `forms/` | Generated single-page, flattened PDFs (15 documents) |
| `ground-truth/` | The exact values on each form, used to test extraction accuracy in Week 1 |
| `textract-fixtures/` | (Week 1) saved Textract responses so tests never call AWS |

## Regenerate

```bash
cd samples
uv run --with pypdf --with reportlab python generate_forms.py
```

## Scenarios

| Client | Documents | What it should exercise |
|---|---|---|
| 01 | W-2 | High earner, no HSA, 401(k) below max |
| 02 | 2 x 1099-NEC | Strong S-corp candidate, no retirement plan |
| 03 | W-2 + 1099-NEC | Side gig: SEP-IRA / Solo 401(k) |
| 04 | W-2 | Control: already uses 401(k) + HSA |
| 05 | 2 x 1099-NEC | S-corp borderline-to-good, Solo 401(k) |
| 06 | W-2 + 1099-NEC | Over Additional Medicare threshold; no state tax (TX) |
| 07 | 1099-NEC | Borderline S-corp: engine should explain why it may not be worth it |
| 08 | W-2 | Control: low income, few opportunities |
| 09 | 1099-NEC | High-income sole prop: S-corp, QBI considerations, Solo 401(k) |
| 10 | 2 x W-2 | Job change: excess Social Security withheld across two employers |

## Simplifications
W-2 boxes are derived from gross pay with simplified rules (documented in `w2_boxes()`):
401(k) reduces box 1 only; cafeteria-plan HSA reduces boxes 1, 3 and 5; withholding uses flat
rates. Good enough for testing extraction and planning logic, not for payroll.
