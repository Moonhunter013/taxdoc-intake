import json
from decimal import Decimal
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.models import W2, Box12Entry, ExtractionResult, FieldMeta, Form1099NEC, FormType
from app.models.common import mask_tin

GROUND_TRUTH = Path(__file__).resolve().parents[2] / "samples" / "ground-truth"
MODELS = {"W-2": W2, "1099-NEC": Form1099NEC}


def ground_truth_documents():
    for path in sorted(GROUND_TRUTH.glob("*.json")):
        truth = json.loads(path.read_text(encoding="utf-8"))
        for doc in truth["documents"]:
            yield pytest.param(doc, id=doc["file"])


@pytest.mark.parametrize("doc", list(ground_truth_documents()))
def test_every_ground_truth_document_round_trips(doc):
    """The models must accept every expected answer and preserve each value exactly,
    otherwise the accuracy tests would be comparing against something else."""
    fields = {k: v for k, v in doc.items() if k not in ("file", "form")}
    dumped = MODELS[doc["form"]](**fields).model_dump()
    for key, expected in fields.items():
        actual = dumped[key]
        if isinstance(actual, Decimal):
            assert actual == Decimal(str(expected)), key
        elif key == "box12":
            assert [(e["code"], Decimal(str(e["amount"]))) for e in expected] == [
                (e["code"], e["amount"]) for e in actual
            ]
        else:
            assert actual == expected, key


def valid_w2(**overrides):
    data = {
        "employer_name": "Halcyon Robotics Inc",
        "employer_ein": "00-0412201",
        "employer_address": "1200 Pearl Pkwy, Boulder, CO 80301",
        "employee_ssn": "000-14-2201",
        "box1_wages": "203000.00",
        "box2_federal_withholding": "42630.00",
        "box3_ss_wages": "184500.00",
        "box4_ss_tax": "11439.00",
        "box5_medicare_wages": "212000.00",
        "box6_medicare_tax": "3182.00",
        "box12": [{"code": "D", "amount": "9000.00"}],
        "box13_retirement_plan": True,
        "box15_state": "CO",
        "box15_state_id": "00012201",
        "box16_state_wages": "203000.00",
        "box17_state_tax": "8932.00",
    }
    data.update(overrides)
    return data


def test_money_is_decimal_not_float():
    w2 = W2(**valid_w2())
    assert isinstance(w2.box1_wages, Decimal)
    assert w2.box1_wages == Decimal("203000.00")


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("box1_wages", "-1.00"),  # negative money
        ("box2_federal_withholding", "10.123"),  # more than 2 decimal places
        ("employer_ein", "12-345"),  # malformed EIN
        ("employee_ssn", "000142201"),  # SSN without dashes
        ("box15_state", "Colorado"),  # not a 2-letter code
        ("box12", [{"code": "d", "amount": "1.00"}]),  # lower-case box 12 code
        ("unexpected_field", "x"),  # unknown fields are rejected
    ],
)
def test_w2_rejects_invalid_values(field, value):
    with pytest.raises(ValidationError):
        W2(**valid_w2(**{field: value}))


def test_w2_state_amounts_require_a_state():
    with pytest.raises(ValidationError, match="box 15 state is missing"):
        W2(**valid_w2(box15_state=None))


def test_tax_ids_never_appear_in_repr():
    text = repr(W2(**valid_w2()))
    assert "000-14-2201" not in text
    assert "00-0412201" not in text
    assert "Halcyon Robotics Inc" in text


def test_models_are_immutable():
    w2 = W2(**valid_w2())
    with pytest.raises(ValidationError):
        w2.box1_wages = Decimal("1.00")


@pytest.mark.parametrize(
    ("tin", "masked"),
    [("000-14-2201", "***-**-2201"), ("00-0412201", "**-***2201"), (None, "")],
)
def test_mask_tin(tin, masked):
    assert mask_tin(tin) == masked


def test_unknown_box12_code_is_allowed_but_detectable():
    entry = Box12Entry(code="ZZ", amount="5.00")
    assert not entry.is_known_code
    assert Box12Entry(code="D", amount="5.00").is_known_code


def test_1099nec_accepts_ssn_or_ein_and_rejects_orphan_state_income():
    base = {
        "payer_name": "Northwoods Software Co",
        "payer_tin": "00-0713092",
        "payer_address": "1 E Main St, Madison, WI 53703",
        "recipient_tin": "000-71-3092",
        "box1a_nonemployee_comp": "95000.00",
    }
    assert Form1099NEC(**base).box1a_nonemployee_comp == Decimal("95000.00")
    with pytest.raises(ValidationError):
        Form1099NEC(**base, box7_state_income="95000.00")


def test_extraction_result_needs_review():
    doc = W2(**valid_w2())
    clean = ExtractionResult(
        form_type=FormType.W2, document=doc, fields={"box1_wages": FieldMeta(confidence=99.1)}
    )
    assert not clean.needs_review
    flagged = ExtractionResult(
        form_type=FormType.W2,
        document=doc,
        fields={"box1_wages": FieldMeta(confidence=71.0, needs_review=True)},
    )
    assert flagged.needs_review
