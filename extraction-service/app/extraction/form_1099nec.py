"""Map a Textract response for Form 1099-NEC (Rev. December 2026, Copy B) to `Form1099NEC`.

Two things differ from the W-2:

1. Payer and recipient use the *same* printed labels ("Street address", "City or town",
   "State or province", "ZIP or foreign postal code"). They're told apart by position: the
   payer block sits above the "RECIPIENT'S name" label, the recipient block below it.
2. The state row (boxes 5-7) has two lines per box, and Textract's key-value detection
   sometimes drops the value entirely (client-05_1099nec_2 lost "$ 49,000.00" and the
   state ID). When that happens we read the text line directly under the box label.
"""

from __future__ import annotations

import re

from pydantic import ValidationError

from app.extraction.errors import IncompleteExtractionError
from app.extraction.normalize import clean_tin, normalize_address_lines, parse_money
from app.extraction.textract import KeyValue, TextractDocument
from app.models import ExtractionResult, FieldMeta, Form1099NEC, FormType

REQUIRED = ["payer_name", "payer_tin", "payer_address", "box1a_nonemployee_comp"]

_STATE_AND_ID = re.compile(r"^(?P<state>[A-Za-z]{2})\s+(?P<id>\S.*)$")
_BOX6_LABEL = re.compile(r"^6\s+State/Payer")
_BOX7_LABEL = re.compile(r"^7\s+State income")

_ADDRESS_PARTS = {
    "street": "street address",
    "city": "city or town",
    "state": "state or province",
    "zip": "zip or foreign postal code",
}


def _one(kvs: list[KeyValue], phrase: str) -> KeyValue | None:
    matches = [kv for kv in kvs if phrase in kv.label]
    return max(matches, key=lambda kv: kv.confidence, default=None)


def _address(kvs: list[KeyValue]) -> tuple[str | None, float]:
    parts = {name: _one(kvs, phrase) for name, phrase in _ADDRESS_PARTS.items()}
    if not all(kv and kv.value for kv in parts.values()):
        return None, 0.0
    text = normalize_address_lines(
        [
            parts["street"].value,
            f"{parts['city'].value}, {parts['state'].value} {parts['zip'].value}",
        ]
    )
    return text, min(kv.confidence for kv in parts.values())


def map_1099nec(doc: TextractDocument) -> ExtractionResult:
    kvs = doc.key_values
    warnings: list[str] = []
    values: dict = {}
    meta: dict[str, FieldMeta] = {}

    def record(name: str, value, confidence: float, source: str | None):
        values[name] = value
        meta[name] = FieldMeta(confidence=confidence, source_label=source)

    # Only the payer block (above "RECIPIENT'S name") is used for the address; the recipient's
    # address uses the same labels further down and isn't part of the model yet.
    recipient_label = _one(kvs, "recipients name")
    split = recipient_label.box.top if recipient_label else 0.4
    payer_kvs = [kv for kv in kvs if kv.box and kv.box.top < split]

    if (kv := _one(payer_kvs, "payers name")) and kv.value:
        record("payer_name", " ".join(kv.value.split()), kv.confidence, kv.raw_label)
    if (kv := _one(kvs, "payers tin")) and kv.value:
        record("payer_tin", clean_tin(kv.value), kv.confidence, kv.raw_label)
    if (kv := _one(kvs, "recipients tin")) and kv.value:
        record("recipient_tin", clean_tin(kv.value), kv.confidence, kv.raw_label)
    if recipient_label and recipient_label.value:
        record(
            "recipient_name",
            " ".join(recipient_label.value.split()),
            recipient_label.confidence,
            recipient_label.raw_label,
        )

    address, conf = _address(payer_kvs)
    if address:
        record("payer_address", address, conf, "payer address block")
    else:
        warnings.append("payer address: street, city, state or ZIP not found")

    for name, phrase in (
        ("box1a_nonemployee_comp", "nonemployee compensation"),
        ("box4_federal_withholding", "federal income tax withheld"),
    ):
        kv = _one(kvs, phrase)
        amount = parse_money(kv.value) if kv else None
        if amount is not None:
            record(name, amount, kv.confidence, kv.raw_label)

    # Box 6: "SC 00027553" -> state "SC", id "00027553". Key-value first, then the text line.
    kv = _one(kvs, "state payers state no")
    box6_text, box6_conf, box6_src = (
        (kv.value, kv.confidence, kv.raw_label) if kv else ("", 0, None)
    )
    if not box6_text and (line := doc.line_below(_BOX6_LABEL)):
        box6_text, box6_conf, box6_src = line.text, line.confidence, "line under box 6 label"
    if m := _STATE_AND_ID.match(box6_text.strip()):
        record("box6_state", m["state"].upper(), box6_conf, box6_src)
        record("box6_state_id", m["id"].strip(), box6_conf, box6_src)

    # Box 7: same fallback. "$ 118,500.00 $" -> first amount (first state row).
    kv = _one(kvs, "7 state income")
    amount = parse_money(kv.value) if kv else None
    conf, src = (kv.confidence, kv.raw_label) if kv else (0, None)
    if amount is None and (line := doc.line_below(_BOX7_LABEL)):
        amount, conf, src = parse_money(line.text), line.confidence, "line under box 7 label"
    if amount is not None:
        if "box6_state" in values:
            record("box7_state_income", amount, conf, src)
        else:
            warnings.append(f"box 7: ignored {amount} because box 6 has no state")

    missing = [name for name in REQUIRED if name not in values]
    if missing:
        raise IncompleteExtractionError("1099-NEC", missing, values, warnings)
    try:
        document = Form1099NEC(**values)
    except ValidationError as exc:
        bad = sorted({str(err["loc"][0]) for err in exc.errors() if err["loc"]})
        raise IncompleteExtractionError("1099-NEC", bad, values, [*warnings, str(exc)]) from exc

    return ExtractionResult(
        form_type=FormType.NEC_1099, document=document, fields=meta, warnings=warnings
    )
