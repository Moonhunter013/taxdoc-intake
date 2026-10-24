"""Helpers for handling taxpayer identification numbers (SSNs and EINs) without storing them.

We keep only:
* the last 4 digits, for display ("***-**-2201"), and
* a keyed HMAC-SHA256 hash, so two documents for the same person can be matched.

A plain SHA-256 of an SSN would be useless as protection: there are only 10^9 possible SSNs,
so every hash can be reversed by brute force in minutes. A *keyed* hash (HMAC) can't be
reversed without the secret key, which lives outside the database.
"""

from __future__ import annotations

import hashlib
import hmac
import re

_SSN = re.compile(r"^\d{3}-\d{2}-\d{4}$")
_EIN = re.compile(r"^\d{2}-\d{7}$")


class MissingHmacKeyError(RuntimeError):
    pass


def tin_kind(tin: str) -> str:
    """'ssn' or 'ein', from the dash pattern. Both are 9 digits, so the kind is part of the
    hash input: otherwise SSN 123-45-6789 and EIN 12-3456789 would hash identically."""
    if _SSN.match(tin):
        return "ssn"
    if _EIN.match(tin):
        return "ein"
    raise ValueError("not a formatted SSN or EIN")


def tin_hash(tin: str, key: str) -> str:
    if not key:
        raise MissingHmacKeyError("EXTRACTION_TIN_HMAC_KEY is not set")
    digits = re.sub(r"\D", "", tin)
    message = f"{tin_kind(tin)}:{digits}".encode()
    return hmac.new(key.encode(), message, hashlib.sha256).hexdigest()


def tin_last4(tin: str) -> str:
    return re.sub(r"\D", "", tin)[-4:]
