# Week 1: Document extraction pipeline

Goal: upload a W-2 or 1099-NEC and get validated, structured JSON back, with low-confidence fields
flagged for review. Tested offline against saved Textract responses.

Suggested order (each builds on the previous):

| # | Issue | Depends on |
|---|---|---|
| 1 | Capture Textract fixtures | - |
| 2 | Pydantic models | - |
| 3 | Textract parser + W-2 mapper | 1, 2 |
| 4 | 1099-NEC mapper | 3 |
| 5 | Accuracy tests vs ground truth | 3, 4 |
| 6 | Confidence + consistency checks | 3 |
| 7 | Upload endpoint | 3, 6 |
| 8 | MySQL persistence | 7 |
| 9 | ADRs 0003-0005 | alongside 6-8 |

Create them on GitHub with `create-issues.ps1` (see the comments at the top), or paste each file in by hand.

**Done when:** `curl -F file=@samples/forms/client-01_w2.pdf localhost:8000/api/v1/documents`
returns correct JSON, the accuracy suite passes in CI, and the ADRs are written.
