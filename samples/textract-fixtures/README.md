# Textract fixtures

Saved AWS Textract `AnalyzeDocument` (FORMS) responses, one JSON file per PDF in `../forms/`.
Tests parse these instead of calling AWS, so CI needs no credentials and costs nothing.

`ResponseMetadata` (request IDs, HTTP headers) is stripped so re-captures only differ when
Textract's actual output changes.

## Capture / re-capture

```powershell
cd extraction-service
uv run python scripts/capture_fixtures.py          # only forms without a fixture
uv run python scripts/capture_fixtures.py --force  # everything
```

Uses the `taxdoc` AWS profile and `EXTRACTION_S3_BUCKET` from the repo-root `.env`.
Re-capture when the sample forms change, or to check whether Textract's model has changed
(the response includes `AnalyzeDocumentModelVersion`).
