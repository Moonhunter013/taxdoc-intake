"""Response shapes for the documents API. SSNs/EINs are always masked in responses."""

from __future__ import annotations

from typing import Any
from uuid import UUID

from pydantic import BaseModel, Field

from app.models import FieldMeta, FormType
from app.models.common import TIN_FIELDS, mask_tin
from app.services.documents import StoredDocument, jsonable


def masked(values: dict[str, Any]) -> dict[str, Any]:
    return {k: (mask_tin(v) if k in TIN_FIELDS and v else v) for k, v in values.items()}


class DocumentResponse(BaseModel):
    document_id: UUID
    form_type: FormType
    tax_year: int
    client_ref: str | None
    needs_review: bool
    document: dict[str, Any]
    fields: dict[str, FieldMeta]
    review_reasons: list[str]
    warnings: list[str]

    @classmethod
    def from_stored(cls, doc: StoredDocument) -> DocumentResponse:
        # Stored TIN values are already masked (ADR-0005); masking again is a no-op safeguard.
        return cls(
            document_id=doc.id,
            form_type=doc.form_type,
            tax_year=doc.tax_year,
            client_ref=doc.client_ref,
            needs_review=doc.needs_review,
            document=masked(jsonable(doc.values)),
            fields=doc.fields,
            review_reasons=doc.review_reasons,
            warnings=doc.warnings,
        )


class CorrectionRequest(BaseModel):
    value: Any = Field(description='The corrected value, e.g. "203000.00" or "CO"')
    corrected_by: str = Field(min_length=1, max_length=255, description="Who made the change")


class IncompleteDocumentResponse(BaseModel):
    detail: str
    form_type: str
    missing_fields: list[str]
    partial: dict[str, Any]
    warnings: list[str]


# Letters, digits, '-' and '_' only: safe in URLs and logs, and can't smuggle path segments.
CLIENT_REF_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$"


class ClientDocumentsResponse(BaseModel):
    client_ref: str
    documents: list[DocumentResponse]
    taxpayer_groups: list[list[UUID]] = Field(
        description="Document IDs grouped by taxpayer. More than one group = check for misfiling"
    )
    warnings: list[str]
