from decimal import Decimal

import pytest

from app.extraction.normalize import (
    clean_tin,
    normalize_address_lines,
    normalize_label,
    parse_money,
)


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("$ 118,500.00", Decimal("118500.00")),
        ("76,500.00", Decimal("76500.00")),
        ("551.00", Decimal("551.00")),
        ("$ 118,500.00 $", Decimal("118500.00")),  # two-row box: first amount wins
        ("$", None),  # empty box with a printed dollar sign
        ("15", None),  # a box number, not money
        ("", None),
        (None, None),
    ],
)
def test_parse_money(text, expected):
    assert parse_money(text) == expected


def test_normalize_label_ignores_apostrophes_and_punctuation():
    assert normalize_label("c Employer’s name, address, and ZIP code") == (
        "c employers name address and zip code"
    )
    assert normalize_label("b Employer identification number (EIN)") == (
        "b employer identification number ein"
    )


def test_normalize_address_uppercases_state():
    assert normalize_address_lines(["1200 Pearl Pkwy", "Boulder, co 80301"]) == (
        "1200 Pearl Pkwy, Boulder, CO 80301"
    )


def test_clean_tin_removes_spaces():
    assert clean_tin("00 - 0412201") == "00-0412201"
    assert clean_tin("") is None
