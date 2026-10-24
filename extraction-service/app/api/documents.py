"""POST /api/v1/documents: upload a W-2 or 1099-NEC PDF and get the extracted data back.

Pipeline: validate the upload -> store it in S3 under a server-generated key -> Textract
(AnalyzeDocument, FORMS) -> map to a typed model -> review rules -> JSON.
"""

from __future__ import annotations

import io
import logging
from typing import Annotated
from uuid import UUID, uuid4

from botocore.exceptions import BotoCoreError, ClientError
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from pypdf import PdfReader
from pypdf.errors import PdfReadError

from app.api.dependencies import (
    get_repository,
    get_s3_client,
    get_settings,
    get_textract_client,
)
from app.api.schemas import (
    CorrectionRequest,
    DocumentResponse,
    IncompleteDocumentResponse,
    masked,
)
from app.config import Settings
from app.db.repository import DocumentRepository
from app.extraction import IncompleteExtractionError, UnsupportedFormError, extract_document
from app.review import review
from app.security import MissingHmacKeyError
from app.services.documents import UnknownFieldError, apply_correction, from_extraction

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/documents", tags=["documents"])


def _validate_pdf(data: bytes, max_bytes: int) -> None:
    if len(data) > max_bytes:
        raise HTTPException(
            status.HTTP_413_CONTENT_TOO_LARGE, f"File is larger than {max_bytes // 1_048_576} MB"
        )
    # Check the file's actual content, not the client-supplied filename or content type.
    if not data.startswith(b"%PDF-"):
        raise HTTPException(status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, "File is not a PDF")
    try:
        pages = len(PdfReader(io.BytesIO(data)).pages)
    except (PdfReadError, ValueError) as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, "PDF could not be read") from exc
    if pages != 1:
        # Synchronous Textract handles one page; each W-2/1099 copy is a single page.
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_CONTENT,
            f"Expected a single-page form, got {pages} pages",
        )


@router.post(
    "",
    response_model=DocumentResponse,
    status_code=status.HTTP_201_CREATED,
    responses={
        413: {"description": "File too large"},
        415: {"description": "Not a PDF"},
        422: {"model": IncompleteDocumentResponse, "description": "Unreadable or incomplete"},
        502: {"description": "Storage or OCR service unavailable"},
    },
)
async def upload_document(
    file: Annotated[UploadFile, File(description="Single-page W-2 or 1099-NEC PDF")],
    settings: Annotated[Settings, Depends(get_settings)],
    s3: Annotated[object, Depends(get_s3_client)],
    textract: Annotated[object, Depends(get_textract_client)],
    repo: Annotated[DocumentRepository, Depends(get_repository)],
    tax_year: Annotated[int | None, Form(ge=2000, le=2100)] = None,
):
    data = await file.read(settings.max_upload_bytes + 1)
    _validate_pdf(data, settings.max_upload_bytes)

    document_id = uuid4()
    key = f"documents/{document_id}.pdf"  # never derived from the client's filename
    try:
        s3.put_object(Bucket=settings.s3_bucket, Key=key, Body=data, ContentType="application/pdf")
        response = textract.analyze_document(
            Document={"S3Object": {"Bucket": settings.s3_bucket, "Name": key}},
            FeatureTypes=["FORMS"],
        )
    except (ClientError, BotoCoreError):
        # Full details go to the server log only; clients get a generic message.
        logger.exception("AWS call failed for document %s", document_id)
        raise HTTPException(
            status.HTTP_502_BAD_GATEWAY, "Document storage or OCR service unavailable"
        ) from None

    year = tax_year or settings.default_tax_year
    try:
        result = review(extract_document(response), tax_year=year, settings=settings)
    except UnsupportedFormError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, str(exc)) from exc
    except IncompleteExtractionError as exc:
        body = IncompleteDocumentResponse(
            detail=str(exc),
            form_type=exc.form_type,
            missing_fields=exc.missing,
            partial=masked({k: str(v) if v is not None else None for k, v in exc.partial.items()}),
            warnings=exc.warnings,
        )
        return JSONResponse(status_code=422, content=body.model_dump())

    # Only successful extractions are stored. Incomplete ones return 422 above; their PDF stays
    # in S3 until the bucket's 30-day expiry.
    try:
        stored = from_extraction(document_id, key, year, result, settings)
    except MissingHmacKeyError:
        logger.error("EXTRACTION_TIN_HMAC_KEY is not configured; refusing to store document")
        raise HTTPException(
            status.HTTP_500_INTERNAL_SERVER_ERROR, "Server configuration error"
        ) from None
    repo.add(stored)
    return DocumentResponse.from_stored(stored)


def _load(repo: DocumentRepository, document_id: UUID):
    doc = repo.get(document_id)
    if doc is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Document not found")
    return doc


@router.get("/{document_id}", response_model=DocumentResponse)
def get_document(document_id: UUID, repo: Annotated[DocumentRepository, Depends(get_repository)]):
    return DocumentResponse.from_stored(_load(repo, document_id))


@router.patch(
    "/{document_id}/fields/{field_name}",
    response_model=DocumentResponse,
    responses={
        404: {"description": "Unknown document or field"},
        422: {"description": "Invalid value"},
    },
)
def correct_field(
    document_id: UUID,
    field_name: str,
    body: CorrectionRequest,
    repo: Annotated[DocumentRepository, Depends(get_repository)],
    settings: Annotated[Settings, Depends(get_settings)],
):
    """Correct one extracted value. The whole document is re-validated and the review rules
    re-run, so fixing one box can clear (or raise) other flags."""
    doc = _load(repo, document_id)
    try:
        updated = apply_correction(doc, field_name, body.value, body.corrected_by, settings)
    except UnknownFieldError:
        raise HTTPException(
            status.HTTP_404_NOT_FOUND, f"{doc.form_type} has no field '{field_name}'"
        ) from None
    except ValidationError as exc:
        # Report which rule failed, but never echo the submitted value (it may be an SSN).
        messages = sorted({err["msg"] for err in exc.errors()})
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_CONTENT,
            f"Invalid value for {field_name}: {'; '.join(messages)}",
        ) from None
    repo.update(updated)
    return DocumentResponse.from_stored(updated)
