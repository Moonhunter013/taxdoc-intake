"""Turn raw AWS Textract responses into typed tax document models."""

from app.extraction.errors import IncompleteExtractionError
from app.extraction.textract import KeyValue, Line, TextractDocument
from app.extraction.w2 import map_w2

__all__ = ["TextractDocument", "KeyValue", "Line", "map_w2", "IncompleteExtractionError"]
