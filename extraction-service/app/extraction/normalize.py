"""Small, pure helpers that clean OCR text. Each is easy to test on its own."""

from __future__ import annotations

import re
from decimal import Decimal

_MONEY = re.compile(r"\d{1,3}(?:,\d{3})+\.\d{2}|\d+\.\d{2}")
_CITY_STATE_ZIP = re.compile(
    r"^(?P<city>.+?),\s*(?P<state>[A-Za-z]{2})\s+(?P<zip>\d{5}(?:-\d{4})?)$"
)


def normalize_label(text: str) -> str:
    """'c Employer’s name, address, and ZIP code' -> 'c employers name address and zip code'.

    Lower-cases, drops apostrophes (straight or curly) and turns other punctuation into
    spaces, so label matching isn't thrown off by small OCR differences.
    """
    text = text.lower().replace("’", "").replace("'", "")
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return " ".join(text.split())


def parse_money(text: str | None) -> Decimal | None:
    """Return the first dollar amount in `text`, or None if there isn't one.

    Handles "$ 118,500.00", "118,500.00", and multi-row values like "$ 118,500.00 $"
    (box 7 on the 1099-NEC has two state rows). Requires exactly two decimals so stray
    digits such as box numbers ("15") are never mistaken for money.
    """
    if not text:
        return None
    match = _MONEY.search(text)
    return Decimal(match.group().replace(",", "")) if match else None


def clean_tin(text: str | None) -> str | None:
    """Remove spaces OCR sometimes inserts: '00 - 0412201' -> '00-0412201'."""
    if not text:
        return None
    return re.sub(r"\s+", "", text)


def normalize_address_lines(lines: list[str]) -> str:
    """Join address lines with ', ' and upper-case the state in a final 'City, ST 12345'.

    OCR occasionally lower-cases state codes ('Boulder, co 80301'); state codes are always
    upper-case, so fixing that is safe.
    """
    lines = [" ".join(line.split()) for line in lines if line.strip()]
    if lines:
        m = _CITY_STATE_ZIP.match(lines[-1])
        if m:
            lines[-1] = f"{m['city']}, {m['state'].upper()} {m['zip']}"
    return ", ".join(lines)
