from decimal import Decimal

import pytest

from app.config import Settings
from app.extraction import extract_document
from app.models import Box12Entry
from app.review import review
from tests.fixtures import fixture_names, load_fixture


def extracted(name: str):
    return extract_document(load_fixture(name))


def with_document_changes(result, **changes):
    """Simulate an OCR misread by changing values on the (immutable) document."""
    return result.model_copy(update={"document": result.document.model_copy(update=changes)})


def with_confidence(result, field: str, confidence: float):
    fields = dict(result.fields)
    fields[field] = fields[field].model_copy(update={"confidence": confidence})
    return result.model_copy(update={"fields": fields})


@pytest.mark.parametrize("name", fixture_names("*"))
def test_clean_sample_forms_are_not_flagged(name):
    """Calibration check: every sample form was extracted correctly, so none should be
    flagged. If this fails after changing thresholds, the thresholds are too strict."""
    result = review(extracted(name))
    assert result.review_reasons == []
    assert not result.needs_review
    assert all(meta.review_reasons == [] for meta in result.fields.values())


def test_low_confidence_on_a_money_field_is_flagged():
    result = review(with_confidence(extracted("client-01_w2"), "box1_wages", 85.0))
    assert result.fields["box1_wages"].needs_review
    assert "box1_wages: confidence 85.0% is below 90%" in result.review_reasons


def test_state_fields_use_their_own_lower_threshold():
    result = extracted("client-01_w2")
    assert result.fields["box15_state"].confidence < 75  # measured ~73%
    assert not review(result).fields["box15_state"].needs_review
    assert review(with_confidence(result, "box15_state", 60.0)).fields["box15_state"].needs_review


def test_thresholds_come_from_settings():
    strict = Settings(review_default_threshold=99.0, review_field_thresholds={})
    result = review(extracted("client-01_w2"), settings=strict)
    assert result.fields["box1_wages"].needs_review
    assert result.fields["box15_state"].needs_review


def test_misread_social_security_tax_is_caught_even_with_high_confidence():
    bad = with_document_changes(extracted("client-01_w2"), box4_ss_tax=Decimal("1439.00"))
    result = review(bad)
    assert result.fields["box4_ss_tax"].needs_review
    assert any("6.2% of box 3" in r for r in result.review_reasons)


def test_each_reason_is_attached_to_the_field_it_concerns():
    """#26: per-field reasons, so a UI never has to guess a field from the wording."""
    bad = with_document_changes(extracted("client-01_w2"), box4_ss_tax=Decimal("1439.00"))
    result = review(with_confidence(bad, "box1_wages", 85.0))

    assert result.fields["box1_wages"].review_reasons == [
        "box1_wages: confidence 85.0% is below 90%"
    ]
    [ss_reason] = result.fields["box4_ss_tax"].review_reasons
    assert "6.2% of box 3" in ss_reason
    # Fields the rules don't mention stay clean, even box 3, which the box 4 rule reads.
    assert result.fields["box3_ss_wages"].review_reasons == []
    # Every per-field reason is also in the document-level list, in the same words.
    per_field = [r for meta in result.fields.values() for r in meta.review_reasons]
    assert sorted(per_field) == sorted(result.review_reasons)


def test_a_field_can_have_more_than_one_reason():
    bad = with_document_changes(extracted("client-01_w2"), box4_ss_tax=Decimal("1439.00"))
    result = review(with_confidence(bad, "box4_ss_tax", 50.0))

    reasons = result.fields["box4_ss_tax"].review_reasons
    assert len(reasons) == 2
    assert reasons[0].startswith("box4_ss_tax: confidence 50.0%")
    assert "6.2% of box 3" in reasons[1]


def test_ss_wages_above_wage_base_are_flagged():
    bad = with_document_changes(
        extracted("client-01_w2"),
        box3_ss_wages=Decimal("212000.00"),
        box4_ss_tax=Decimal("13144.00"),  # consistent 6.2%, so only the wage-base rule fires
    )
    reasons = review(bad).review_reasons
    assert reasons == ["box 3 $212,000.00 exceeds the 2026 wage base $184,500.00"]


def test_medicare_tax_below_minimum_is_flagged():
    bad = with_document_changes(extracted("client-08_w2"), box6_medicare_tax=Decimal("51.00"))
    assert review(bad).fields["box6_medicare_tax"].needs_review


def test_additional_medicare_tax_is_not_mistaken_for_an_error():
    # client-06 has box 5 above $200k, so box 6 is more than 1.45%. That's correct.
    assert review(extracted("client-06_w2")).review_reasons == []


def test_unknown_box12_code_is_flagged_not_rejected():
    result = extracted("client-04_w2")
    bad = with_document_changes(result, box12=[Box12Entry(code="ZZ", amount=Decimal("5.00"))])
    assert any("'ZZ'" in r for r in review(bad).review_reasons)


@pytest.mark.parametrize("code", ["TA", "TP", "TT"])
def test_box12_codes_new_for_2026_are_not_flagged(code):
    # TA, TP and TT were added in the 2026 W-2/W-3 instructions; they must not be
    # reported as unknown codes.
    result = extracted("client-04_w2")
    with_code = with_document_changes(
        result, box12=[Box12Entry(code=code, amount=Decimal("500.00"))]
    )
    assert review(with_code).review_reasons == []


def test_unconfigured_tax_year_is_flagged():
    reasons = review(extracted("client-01_w2"), tax_year=2031).review_reasons
    assert "no Social Security wage base configured for 2031" in reasons


def test_1099_state_income_above_compensation_is_flagged():
    bad = with_document_changes(
        extracted("client-02_1099nec_1"), box7_state_income=Decimal("999999.00")
    )
    assert review(bad).fields["box7_state_income"].needs_review


def test_review_never_changes_values():
    original = extracted("client-01_w2")
    reviewed = review(with_confidence(original, "box1_wages", 10.0))
    assert reviewed.document == original.document
