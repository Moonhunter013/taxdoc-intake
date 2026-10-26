"""The stored form of an extracted document, and the rules for changing it.

`StoredDocument` is what gets persisted: extracted values with SSNs/EINs already reduced to a
masked display value plus a keyed hash (ADR-0005). The functions here are pure (no database),
so the same logic is used by the MySQL repository and the in-memory one in tests.
"""

from __future__ import annotations

from datetime import UTC, datetime
from decimal import Decimal
from typing import Any
from uuid import UUID

from pydantic import BaseModel, Field

from app.config import Settings
from app.models import W2, ExtractionResult, FieldMeta, Form1099NEC, FormType
from app.models.common import TIN_FIELDS, mask_tin
from app.review import review
from app.security import tin_hash

MODELS: dict[FormType, type[W2] | type[Form1099NEC]] = {
    FormType.W2: W2,
    FormType.NEC_1099: Form1099NEC,
}
# Whose number identifies the taxpayer on each form (used to match a client's documents).
TAXPAYER_TIN_FIELD = {FormType.W2: "employee_ssn", FormType.NEC_1099: "recipient_tin"}

# Valid-format stand-ins used only to re-validate a document whose real TINs we no longer have.
_PLACEHOLDER_TIN = {
    "employee_ssn": "000-00-0000",
    "recipient_tin": "000-00-0000",
    "payer_tin": "00-0000000",
    "employer_ein": "00-0000000",
}


class UnknownFieldError(LookupError):
    pass


class StoredDocument(BaseModel):
    id: UUID
    form_type: FormType
    tax_year: int
    s3_key: str
    client_ref: str | None = None
    values: dict[str, Any] = Field(description="Field values; TIN fields hold masked values")
    fields: dict[str, FieldMeta] = Field(default_factory=dict)
    tin_hashes: dict[str, str] = Field(default_factory=dict)
    review_reasons: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    needs_review: bool = False
    created_at: datetime
    updated_at: datetime

    @property
    def taxpayer_tin_hash(self) -> str | None:
        return self.tin_hashes.get(TAXPAYER_TIN_FIELD[self.form_type])

    @property
    def taxpayer_tin_last4(self) -> str | None:
        masked = self.values.get(TAXPAYER_TIN_FIELD[self.form_type])
        return masked[-4:] if masked else None


def _minimize(values: dict[str, Any], key: str) -> tuple[dict[str, Any], dict[str, str]]:
    values, hashes = dict(values), {}
    for field in TIN_FIELDS:
        if values.get(field):
            hashes[field] = tin_hash(values[field], key)
            values[field] = mask_tin(values[field])
    return values, hashes


def from_extraction(
    document_id: UUID,
    s3_key: str,
    tax_year: int,
    result: ExtractionResult,
    settings: Settings,
    client_ref: str | None = None,
) -> StoredDocument:
    now = datetime.now(UTC)
    values, hashes = _minimize(
        result.document.model_dump(), settings.tin_hmac_key.get_secret_value()
    )
    return StoredDocument(
        id=document_id,
        form_type=result.form_type,
        tax_year=tax_year,
        s3_key=s3_key,
        client_ref=client_ref,
        values=values,
        fields=result.fields,
        tin_hashes=hashes,
        review_reasons=result.review_reasons,
        warnings=result.warnings,
        needs_review=result.needs_review,
        created_at=now,
        updated_at=now,
    )


def _rebuild(stored: StoredDocument, overrides: dict[str, Any]) -> W2 | Form1099NEC:
    """Re-create the typed model (raises pydantic.ValidationError on bad values)."""
    values = dict(stored.values)
    for field, placeholder in _PLACEHOLDER_TIN.items():
        if values.get(field):
            values[field] = placeholder
    values.update(overrides)
    return MODELS[stored.form_type](**values)


def apply_correction(
    stored: StoredDocument, field: str, raw_value: Any, corrected_by: str, settings: Settings
) -> StoredDocument:
    """Apply a human correction to one field, re-validate the whole document and re-run the
    review rules (a corrected box 3 may resolve, or create, a box 4 consistency problem)."""
    if field not in MODELS[stored.form_type].model_fields:
        raise UnknownFieldError(field)
    document = _rebuild(stored, {field: raw_value})  # may raise ValidationError
    new_value = document.model_dump()[field]

    values, hashes = dict(stored.values), dict(stored.tin_hashes)
    if field in TIN_FIELDS:
        if new_value:
            values[field] = mask_tin(new_value)
            hashes[field] = tin_hash(new_value, settings.tin_hmac_key.get_secret_value())
        else:
            values[field] = None
            hashes.pop(field, None)
    else:
        values[field] = new_value

    now = datetime.now(UTC)
    fields = dict(stored.fields)
    meta = fields.get(field) or FieldMeta(confidence=100.0, source_label="manual correction")
    fields[field] = meta.model_copy(update={"corrected_by": corrected_by, "corrected_at": now})

    # A human-corrected value counts as fully confident for the confidence rule.
    for_review = {
        name: m.model_copy(update={"confidence": 100.0}) if m.corrected_by else m
        for name, m in fields.items()
    }
    reviewed = review(
        ExtractionResult(form_type=stored.form_type, document=document, fields=for_review),
        tax_year=stored.tax_year,
        settings=settings,
    )
    fields = {
        name: m.model_copy(
            update={
                "needs_review": reviewed.fields[name].needs_review,
                "review_reasons": reviewed.fields[name].review_reasons,
            }
        )
        for name, m in fields.items()
    }
    return stored.model_copy(
        update={
            "values": values,
            "tin_hashes": hashes,
            "fields": fields,
            "review_reasons": reviewed.review_reasons,
            "needs_review": reviewed.needs_review,
            "updated_at": now,
        }
    )


def group_by_taxpayer(docs: list[StoredDocument]) -> list[list[UUID]]:
    """Group documents by taxpayer (matched on the keyed TIN hash, ADR-0005).

    Documents without a taxpayer TIN form their own group. More than one group for a single
    client usually means a misfiled document (e.g. a spouse's W-2). Only document IDs are
    returned; hashes never leave the service.
    """
    groups: dict[str, list[UUID]] = {}
    for doc in docs:
        key = doc.taxpayer_tin_hash or f"unknown:{doc.id}"
        groups.setdefault(key, []).append(doc.id)
    return list(groups.values())


def jsonable(value: Any) -> Any:
    """Decimals as exact strings ('203000.00'), never floats."""
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, dict):
        return {k: jsonable(v) for k, v in value.items()}
    if isinstance(value, list):
        return [jsonable(v) for v in value]
    return value


__all__ = [
    "StoredDocument",
    "UnknownFieldError",
    "from_extraction",
    "apply_correction",
    "jsonable",
    "group_by_taxpayer",
]
