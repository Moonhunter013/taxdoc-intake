"""Work out which form a Textract response is, and route it to the right mapper."""

from __future__ import annotations

from app.extraction.form_1099nec import map_1099nec
from app.extraction.textract import TextractDocument
from app.extraction.w2 import map_w2
from app.models import ExtractionResult, FormType


class UnsupportedFormError(Exception):
    """The document isn't a form this service knows how to read."""


def detect_form_type(doc: TextractDocument) -> FormType:
    """Both forms print their name next to a "Form" label (e.g. Form -> 'W-2')."""
    names = {kv.value.replace(" ", "").upper() for kv in doc.key_values if kv.label == "form"}
    if "W-2" in names:
        return FormType.W2
    if "1099-NEC" in names:
        return FormType.NEC_1099
    raise UnsupportedFormError(f"Not a W-2 or 1099-NEC (form labels found: {sorted(names)})")


def extract_document(response: dict) -> ExtractionResult:
    doc = TextractDocument(response)
    form_type = detect_form_type(doc)
    return map_w2(doc) if form_type is FormType.W2 else map_1099nec(doc)
