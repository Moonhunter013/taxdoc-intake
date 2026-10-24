"""Decide which extracted values a human should check before they're used (issue 6).

Two independent kinds of check:

1. Confidence: each field's Textract confidence is compared with its threshold
   (`Settings.threshold_for`). Thresholds are per field because Textract's confidence is not
   uniform: the state rows come back at ~72-80% even when correct. See ADR-0004.
2. Consistency: arithmetic that must hold on a real form, e.g. Social Security tax is 6.2% of
   Social Security wages. These catch misreads that Textract was confident about.

Nothing here changes a value. It only sets `needs_review` and explains why in
`review_reasons`, so a person makes the correction.
"""

from __future__ import annotations

from decimal import Decimal

from app.config import Settings
from app.config import settings as default_settings
from app.models import W2, ExtractionResult, Form1099NEC


def _money(x: Decimal) -> str:
    return f"${x:,.2f}"


def _w2_rules(doc: W2, tax_year: int, s: Settings) -> list[tuple[str, str]]:
    """Return (field, reason) pairs for every consistency rule the W-2 breaks."""
    tol = s.review_tolerance
    problems: list[tuple[str, str]] = []

    expected_ss_tax = (doc.box3_ss_wages * s.ss_tax_rate).quantize(Decimal("0.01"))
    if abs(doc.box4_ss_tax - expected_ss_tax) > tol:
        problems.append(
            (
                "box4_ss_tax",
                f"box 4 is {_money(doc.box4_ss_tax)} but 6.2% of box 3 is "
                f"{_money(expected_ss_tax)}",
            )
        )

    wage_base = s.ss_wage_base_by_year.get(tax_year)
    if wage_base is None:
        problems.append(
            ("box3_ss_wages", f"no Social Security wage base configured for {tax_year}")
        )
    elif doc.box3_ss_wages > wage_base + tol:
        problems.append(
            (
                "box3_ss_wages",
                f"box 3 {_money(doc.box3_ss_wages)} exceeds the {tax_year} wage base "
                f"{_money(wage_base)}",
            )
        )

    # Box 6 can be *more* than 1.45% (Additional Medicare Tax above $200k) but never less.
    min_medicare = (doc.box5_medicare_wages * s.medicare_tax_rate).quantize(Decimal("0.01"))
    if doc.box6_medicare_tax + tol < min_medicare:
        problems.append(
            (
                "box6_medicare_tax",
                f"box 6 {_money(doc.box6_medicare_tax)} is less than 1.45% of box 5 "
                f"({_money(min_medicare)})",
            )
        )

    # Pre-tax deductions (401(k)) reduce box 1 but not box 5, so box 1 is normally <= box 5.
    if doc.box1_wages > doc.box5_medicare_wages + tol:
        problems.append(
            (
                "box1_wages",
                f"box 1 {_money(doc.box1_wages)} is more than box 5 "
                f"{_money(doc.box5_medicare_wages)}; unusual, check both",
            )
        )

    for entry in doc.box12:
        if not entry.is_known_code:
            problems.append(("box12", f"box 12 code '{entry.code}' is not a known IRS code"))
    return problems


def _1099nec_rules(doc: Form1099NEC, s: Settings) -> list[tuple[str, str]]:
    tol = s.review_tolerance
    problems: list[tuple[str, str]] = []
    if doc.box4_federal_withholding > doc.box1a_nonemployee_comp + tol:
        problems.append(
            ("box4_federal_withholding", "box 4 withholding is more than box 1a compensation")
        )
    if doc.box7_state_income > doc.box1a_nonemployee_comp + tol:
        problems.append(("box7_state_income", "box 7 state income is more than box 1a"))
    return problems


def review(
    result: ExtractionResult,
    tax_year: int | None = None,
    settings: Settings | None = None,
) -> ExtractionResult:
    """Return a copy of `result` with `needs_review` flags and `review_reasons` filled in."""
    s = settings or default_settings
    year = tax_year or s.default_tax_year

    flagged: set[str] = set()
    reasons: list[str] = []

    for name, meta in result.fields.items():
        threshold = s.threshold_for(name)
        if meta.confidence < threshold:
            flagged.add(name)
            reasons.append(f"{name}: confidence {meta.confidence:.1f}% is below {threshold:.0f}%")

    doc = result.document
    rules = _w2_rules(doc, year, s) if isinstance(doc, W2) else _1099nec_rules(doc, s)
    for name, reason in rules:
        flagged.add(name)
        reasons.append(reason)

    fields = {
        name: meta.model_copy(update={"needs_review": name in flagged})
        for name, meta in result.fields.items()
    }
    return result.model_copy(update={"fields": fields, "review_reasons": reasons})
