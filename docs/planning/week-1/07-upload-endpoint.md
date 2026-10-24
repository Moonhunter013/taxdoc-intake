## Why
The UI and the MCP server need one entry point to submit a document.

## What
`POST /api/v1/documents` (multipart PDF upload) ->
validate -> store in S3 -> Textract -> parse/validate -> return extracted document JSON.

## Acceptance criteria
- [ ] Rejects non-PDFs (check magic bytes `%PDF`, not just content type), files > 5 MB, and multi-page PDFs (sync Textract is single-page)
- [ ] S3 key is server-generated (`documents/<uuid>.pdf`); the client's filename is never used as a path
- [ ] boto3 clients injected via FastAPI dependencies so tests can stub them (botocore Stubber or moto)
- [ ] Errors return clear 4xx/5xx JSON; no stack traces or AWS details leaked
- [ ] OpenAPI docs at `/docs` show the endpoint with an example response
