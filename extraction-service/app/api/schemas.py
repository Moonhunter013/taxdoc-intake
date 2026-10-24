"""Response shapes for the documents API. SSNs/EINs are always masked in responses."""

from __future__ import annotations

from typing import Any
from uuid import UUID

from pydantic import BaseModel

from app.models import ExtractionResult, FieldMeta, FormType
from app.models.common import mask_tin

TIN_FIELDS = ("employee_ssn", "employer_ein", "payer_tin", "recipient_tin")


def masked(values: dict[str, Any]) -> dict[str, Any]:
    return {k: (mask_tin(v) if k in TIN_FIELDS and v else v) for k, v in values.items()}


class DocumentResponse(BaseModel):
    document_id: UUID
    form_type: FormType
    needs_review: bool
    document: dict[str, Any]
    fields: dict[str, FieldMeta]
    review_reasons: list[str]
    warnings: list[str]

    @classmethod
    def build(cls, document_id: UUID, result: ExtractionResult) -> DocumentResponse:
        return cls(
            document_id=document_id,
            form_type=result.form_type,
            needs_review=result.needs_review,
            document=masked(result.document.model_dump(mode="json")),
            fields=result.fields,
            review_reasons=result.review_reasons,
            warnings=result.warnings,
        )


class IncompleteDocumentResponse(BaseModel):
    detail: str
    form_type: str
    missing_fields: list[str]
    partial: dict[str, Any]
    warnings: list[str]
