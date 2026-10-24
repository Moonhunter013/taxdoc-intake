"""Shared field types for tax document models.

Design notes:
* Money is always `Decimal`, never `float`: 0.1 + 0.2 != 0.3 in floating point, and tax
  amounts must add up exactly. Values are limited to 2 decimal places and must be >= 0.
* SSNs/EINs are validated for format only. Fields holding them use `repr=False` so they
  never appear when a model is printed or logged; use `mask_tin()` for display.
"""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StringConstraints

Money = Annotated[Decimal, Field(ge=0, max_digits=12, decimal_places=2)]

SSN = Annotated[str, StringConstraints(pattern=r"^\d{3}-\d{2}-\d{4}$")]
EIN = Annotated[str, StringConstraints(pattern=r"^\d{2}-\d{7}$")]
# 1099 payer/recipient TINs can be either an SSN or an EIN.
TIN = Annotated[str, StringConstraints(pattern=r"^(\d{3}-\d{2}-\d{4}|\d{2}-\d{7})$")]

StateCode = Annotated[str, StringConstraints(pattern=r"^[A-Z]{2}$")]

# Every model field that holds an SSN or EIN. These are masked in API responses and never
# stored in full (see docs/adr/0005-sensitive-data-handling.md).
TIN_FIELDS = ("employee_ssn", "employer_ein", "payer_tin", "recipient_tin")

# Well-established W-2 box 12 codes (IRS General Instructions for Forms W-2 and W-3).
# The IRS adds codes occasionally, so the model only checks the *format* of a code;
# the review layer flags codes not in this set instead of rejecting the document.
# TODO: confirm against the 2026 instructions for any newly added codes.
KNOWN_BOX12_CODES = frozenset(
    "A B C D E F G H J K L M N P Q R S T V W Y Z AA BB DD EE FF GG HH II".split()
)


def mask_tin(tin: str | None) -> str:
    """'000-14-2201' -> '***-**-2201'; '00-0412201' -> '**-***2201'."""
    if not tin:
        return ""
    return "".join("*" if c.isdigit() else c for c in tin[:-4]) + tin[-4:]


class StrictModel(BaseModel):
    """Base for document models: unknown fields are errors, instances are immutable."""

    model_config = ConfigDict(extra="forbid", frozen=True, str_strip_whitespace=True)


class Box12Entry(StrictModel):
    code: Annotated[str, StringConstraints(pattern=r"^[A-Z]{1,2}$")]
    amount: Money

    @property
    def is_known_code(self) -> bool:
        return self.code in KNOWN_BOX12_CODES


class FieldMeta(BaseModel):
    """How a single extracted value was obtained and whether a human should check it."""

    confidence: float = Field(ge=0, le=100, description="Textract confidence, 0-100")
    source_label: str | None = Field(default=None, description="Form label the value came from")
    needs_review: bool = False
    corrected_by: str | None = Field(default=None, description="Who manually corrected it")
    corrected_at: datetime | None = None
