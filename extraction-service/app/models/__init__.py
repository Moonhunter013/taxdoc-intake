"""Typed models for extracted tax documents."""

from app.models.common import Box12Entry, FieldMeta
from app.models.extraction import ExtractionResult, FormType
from app.models.form_1099nec import Form1099NEC
from app.models.w2 import W2

__all__ = ["W2", "Form1099NEC", "Box12Entry", "FieldMeta", "ExtractionResult", "FormType"]
