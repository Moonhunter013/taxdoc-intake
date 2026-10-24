## Why
Every extraction test needs real Textract output, but CI must never call AWS (cost, credentials, flakiness).
Capture the responses once, commit them, and test against them forever.

## What
A script `extraction-service/scripts/capture_fixtures.py` that, for each PDF in `samples/forms/`:
1. uploads it to the dev bucket under `fixtures/<name>.pdf`
2. calls Textract `AnalyzeDocument` with `FeatureTypes=["FORMS"]`
3. writes the raw response to `samples/textract-fixtures/<name>.json` (UTF-8, pretty-printed)

## Acceptance criteria
- [ ] 15 fixture files committed, one per sample form
- [ ] Uses `AWS_PROFILE` / default credential chain; no keys in code
- [ ] Skips files that already have a fixture unless `--force` is passed (re-runs cost nothing)
- [ ] Bucket name read from config/env, not hard-coded
- [ ] README note on how and when to re-capture

## Notes
15 pages is well inside the Textract free tier. Writing JSON from Python (not PowerShell redirection)
keeps characters like curly apostrophes intact.
