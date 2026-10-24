from decimal import Decimal

import pytest

from app.extraction import TextractDocument, map_1099nec
from tests.fixtures import expected_document, fixture_names, load_fixture

NEC_FIXTURES = fixture_names("*_1099nec*")


def extract(name: str):
    return map_1099nec(TextractDocument(load_fixture(name)))


def test_all_1099nec_fixtures_are_present():
    assert len(NEC_FIXTURES) == 8


@pytest.mark.parametrize("name", NEC_FIXTURES)
def test_1099nec_matches_ground_truth(name):
    dumped = extract(name).document.model_dump()
    for key, expected in expected_document(name).items():
        actual = dumped[key]
        if isinstance(actual, Decimal):
            assert actual == Decimal(str(expected)), f"{name}: {key}"
        else:
            assert actual == expected, f"{name}: {key}"


def test_payer_and_recipient_addresses_are_not_mixed_up():
    # Both blocks use identical labels ("Street address", "City or town", ...).
    doc = extract("client-02_1099nec_1").document
    assert doc.payer_address == "900 Meeting St, Charleston, SC 29403"
    assert doc.recipient_name == "Marisol Etxeberria"
    assert doc.recipient_tin == "000-27-5532"


def test_state_row_falls_back_to_text_lines():
    # Textract's key-value pairs lost box 6 and box 7 on this form; the text lines did not.
    result = extract("client-05_1099nec_2")
    assert result.document.box6_state == "OR"
    assert result.document.box6_state_id == "00052905"
    assert result.document.box7_state_income == Decimal("49000.00")
    assert result.fields["box7_state_income"].source_label == "line under box 7 label"


def test_no_state_row_for_texas():
    doc = extract("client-06_1099nec").document
    assert doc.box6_state is None
    assert doc.box7_state_income == Decimal("0")


def test_two_row_box7_uses_first_row():
    # "$ 118,500.00 $": the second state row is empty.
    assert extract("client-02_1099nec_1").document.box7_state_income == Decimal("118500.00")
