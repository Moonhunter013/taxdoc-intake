"""Extraction accuracy against ground truth for every sample form (issue 5).

One test per document. Each records a per-field pass/fail via the `accuracy` fixture, and
conftest.py prints a summary table at the end of the run, e.g. "box1_wages  7/7". A failing
test names every field that was wrong, with expected and actual values, so OCR regressions are
easy to read.
"""

from __future__ import annotations

from decimal import Decimal

import pytest

from app.extraction import IncompleteExtractionError, extract_document
from tests.fixtures import expected_document, fixture_names, load_fixture

_MISSING = object()  # distinguishes "not extracted" from an expected None (e.g. no state)


def _same(actual, expected) -> bool:
    if isinstance(actual, Decimal):
        return actual == Decimal(str(expected))
    if isinstance(actual, list):  # box 12 entries
        return [(e["code"], e["amount"]) for e in actual] == [
            (e["code"], Decimal(str(e["amount"]))) for e in expected
        ]
    return actual == expected


@pytest.mark.parametrize("name", fixture_names("*"))
def test_extraction_accuracy(name, accuracy):
    expected = expected_document(name)
    try:
        actual = extract_document(load_fixture(name)).document.model_dump()
    except IncompleteExtractionError as exc:
        actual = exc.partial

    wrong = []
    for field, want in expected.items():
        got = actual.get(field, _MISSING)
        ok = got is not _MISSING and (got == want if got is None else _same(got, want))
        accuracy[field][0] += ok
        accuracy[field][1] += 1
        if not ok:
            wrong.append(f"  {field}: expected {want!r}, got {got!r}")
    assert not wrong, f"{name} has {len(wrong)} wrong field(s):\n" + "\n".join(wrong)
