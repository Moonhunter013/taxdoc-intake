"""Turn raw AWS Textract responses into typed tax document models."""

from app.extraction.dispatch import UnsupportedFormError, detect_form_type, extract_document
from app.extraction.errors import IncompleteExtractionError
from app.extraction.form_1099nec import map_1099nec
from app.extraction.textract import KeyValue, Line, TextractDocument
from app.extraction.w2 import map_w2

__all__ = [
    "TextractDocument",
    "KeyValue",
    "Line",
    "map_w2",
    "map_1099nec",
    "detect_form_type",
    "extract_document",
    "IncompleteExtractionError",
    "UnsupportedFormError",
]
