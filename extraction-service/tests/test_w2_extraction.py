from decimal import Decimal

import pytest

from app.extraction import IncompleteExtractionError, TextractDocument, map_w2
from tests.fixtures import expected_document, fixture_names, load_fixture, without_field

W2_FIXTURES = fixture_names("*_w2*")


def extract(name: str):
    return map_w2(TextractDocument(load_fixture(name)))


def test_all_w2_fixtures_are_present():
    assert len(W2_FIXTURES) == 7


@pytest.mark.parametrize("name", W2_FIXTURES)
def test_w2_matches_ground_truth(name):
    dumped = extract(name).document.model_dump()
    for key, expected in expected_document(name).items():
        actual = dumped[key]
        if key == "box12":
            assert [(e["code"], e["amount"]) for e in actual] == [
                (e["code"], Decimal(str(e["amount"]))) for e in expected
            ], name
        elif isinstance(actual, Decimal):
            assert actual == Decimal(str(expected)), f"{name}: {key}"
        else:
            assert actual == expected, f"{name}: {key}"


def test_box12_read_from_lines_when_key_values_miss_it():
    # client-01's "D 9,000.00" is not in any Textract key-value pair.
    entries = extract("client-01_w2").document.box12
    assert [(e.code, e.amount) for e in entries] == [("D", Decimal("9000.00"))]


def test_box12_multiple_entries_and_none():
    assert [e.code for e in extract("client-06_w2").document.box12] == ["D", "W"]
    assert extract("client-08_w2").document.box12 == []


def test_stateless_w2_ignores_box_number_noise():
    result = extract("client-06_w2")  # Texas: no state income tax row
    assert result.document.box15_state is None
    assert result.document.box17_state_tax == Decimal("0")
    assert any("box 15" in w for w in result.warnings)


def test_employee_name_and_ssn_are_read():
    doc = extract("client-06_w2").document
    assert doc.employee_name == "Helena Vasquez-Moore"
    assert doc.masked_ssn == "***-**-4418"


def test_field_metadata_records_confidence_and_source():
    result = extract("client-01_w2")
    box1 = result.fields["box1_wages"]
    assert box1.confidence > 90
    assert box1.source_label.startswith("1 Wages")
    # State rows come back noticeably less confident; issue 6 decides what to flag.
    assert result.fields["box15_state"].confidence < box1.confidence


def test_missing_required_box_raises_with_partial_result():
    response = without_field(load_fixture("client-01_w2"), "1 Wages")
    with pytest.raises(IncompleteExtractionError) as exc_info:
        map_w2(TextractDocument(response))
    assert exc_info.value.missing == ["box1_wages"]
    assert exc_info.value.partial["box2_federal_withholding"] == Decimal("42630.00")
