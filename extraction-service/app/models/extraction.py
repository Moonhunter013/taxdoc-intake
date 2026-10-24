"""The result of extracting one document: clean values plus how each was obtained."""

from __future__ import annotations

from enum import StrEnum

from pydantic import BaseModel, Field

from app.models.common import FieldMeta
from app.models.form_1099nec import Form1099NEC
from app.models.w2 import W2


class FormType(StrEnum):
    W2 = "W-2"
    NEC_1099 = "1099-NEC"


class ExtractionResult(BaseModel):
    """Separates *what* the document says (`document`) from *how sure we are* (`fields`).

    Keeping metadata out of the W2/Form1099NEC models means those stay simple, immutable
    value objects that downstream code (and the planning service) can trust, while the
    review UI still gets per-field confidence.
    """

    form_type: FormType
    document: W2 | Form1099NEC
    fields: dict[str, FieldMeta] = Field(
        default_factory=dict, description="Keyed by the model field name, e.g. 'box1_wages'"
    )
    review_reasons: list[str] = Field(
        default_factory=list, description="Why a human should check this document"
    )
    warnings: list[str] = Field(
        default_factory=list, description="Notes from mapping, e.g. ignored OCR noise"
    )

    @property
    def needs_review(self) -> bool:
        return bool(self.review_reasons) or any(f.needs_review for f in self.fields.values())
