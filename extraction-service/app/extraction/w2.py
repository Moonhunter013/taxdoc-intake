"""Map a Textract response for a Form W-2 (2026, Copy B) to the `W2` model.

Two strategies, chosen from what Textract actually returns for these forms
(see samples/textract-fixtures):

1. Most boxes: Textract's key-value pairs. Labels are matched by a distinctive phrase
   rather than the box number, so an OCR slip like "l Wages" for "1 Wages" still matches.
2. Box 12: geometry. Textract's key-value detection frequently misses box 12 entirely
   (e.g. client-01: "D 9,000.00" is not in any key-value pair), but the plain text LINEs
   are read reliably. Each 12a-12d label marks the top of a row; the code and amount are
   the short lines inside that row.
"""

from __future__ import annotations

import re

from pydantic import ValidationError

from app.extraction.errors import IncompleteExtractionError
from app.extraction.normalize import clean_tin, normalize_address_lines, parse_money
from app.extraction.textract import KeyValue, Line, TextractDocument
from app.models import W2, Box12Entry, ExtractionResult, FieldMeta, FormType

# field name -> phrase that must appear in the normalized label
_PHRASES: dict[str, str] = {
    "employee_ssn": "social security number",
    "employer_ein": "employer identification number",
    "employer": "employers name address",
    "employee_name": "employees first name",
    "box1_wages": "wages tips other compensation",
    "box2_federal_withholding": "federal income tax withheld",
    "box3_ss_wages": "social security wages",
    "box4_ss_tax": "social security tax withheld",
    "box5_medicare_wages": "medicare wages and tips",
    "box6_medicare_tax": "medicare tax withheld",
    "box13_retirement_plan": "retirement plan",
    "box15_state_id": "state id number",
    "box16_state_wages": "state wages tips",
    "box17_state_tax": "state income tax",
}
_MONEY_FIELDS = [
    "box1_wages",
    "box2_federal_withholding",
    "box3_ss_wages",
    "box4_ss_tax",
    "box5_medicare_wages",
    "box6_medicare_tax",
    "box16_state_wages",
    "box17_state_tax",
]
REQUIRED = ["employer_name", "employer_ein", *_MONEY_FIELDS[:6]]

_STATE_CODE = re.compile(r"^[A-Z]{2}$")
_BOX12_CODE = re.compile(r"^[A-Z]{1,2}$")
_BOX12_AMOUNT = re.compile(r"^\$?\s*[\d,]+\.\d{2}$")
_BOX12_LABEL = re.compile(r"^12([abcd])\b")


def _find(kvs: list[KeyValue], phrase: str, warnings: list[str]) -> KeyValue | None:
    matches = [kv for kv in kvs if phrase in kv.label]
    if len(matches) > 1:
        warnings.append(f"{len(matches)} labels matched '{phrase}'; used the most confident")
    with_value = [kv for kv in matches if kv.value or kv.selected is not None] or matches
    return max(with_value, key=lambda kv: kv.confidence, default=None)


def _box12_entries(lines: list[Line], warnings: list[str]) -> tuple[list[Box12Entry], float]:
    """Read box 12a-12d from text lines.

    Row = from one 12x label's top to the next label's top. Inside a row, the code is a short
    line of 1-2 capital letters and the amount is a money line. Tall, low-confidence lines
    (the tiny rotated "Code" text printed in each row, which Textract reads as 'Code' or
    '0000') are excluded by requiring normal text height.
    """
    labels = sorted(
        ((m.group(1), ln) for ln in lines if (m := _BOX12_LABEL.match(ln.text))),
        key=lambda item: item[1].box.top,
    )
    if not labels:
        return [], 100.0
    column_left = min(ln.box.left for _, ln in labels) - 0.005
    entries, confidences = [], []
    for i, (slot, label) in enumerate(labels):
        row_top = label.box.top + label.box.height
        row_bottom = labels[i + 1][1].box.top if i + 1 < len(labels) else row_top + 0.05
        in_row = [
            ln
            for ln in lines
            if ln.box.left >= column_left
            and row_top <= ln.box.top < row_bottom
            and ln.box.height < 0.025
        ]
        codes = [ln for ln in in_row if _BOX12_CODE.match(ln.text)]
        amounts = [ln for ln in in_row if _BOX12_AMOUNT.match(ln.text)]
        if codes and amounts:
            code, amount = codes[0], amounts[0]
            entries.append(Box12Entry(code=code.text, amount=parse_money(amount.text)))
            confidences.append(min(code.confidence, amount.confidence))
        elif amounts:
            warnings.append(f"box 12{slot}: amount {amounts[0].text} found without a code")
        # A lone letter with no amount is OCR noise from the row's printed "Code" text.
    return entries, min(confidences, default=100.0)


def map_w2(doc: TextractDocument) -> ExtractionResult:
    kvs = doc.key_values
    warnings: list[str] = []
    values: dict = {}
    meta: dict[str, FieldMeta] = {}

    def record(name: str, value, kv: KeyValue | None = None, confidence: float | None = None):
        values[name] = value
        meta[name] = FieldMeta(
            confidence=confidence if confidence is not None else (kv.confidence if kv else 0.0),
            source_label=kv.raw_label if kv else None,
        )

    found = {name: _find(kvs, phrase, warnings) for name, phrase in _PHRASES.items()}

    if kv := found["employer"]:
        if kv.value_lines:
            record("employer_name", kv.value_lines[0], kv)
            record("employer_address", normalize_address_lines(kv.value_lines[1:]), kv)
    for name in ("employer_ein", "employee_ssn"):
        if (kv := found[name]) and kv.value:
            record(name, clean_tin(kv.value), kv)
    if (kv := found["employee_name"]) and kv.value:
        record("employee_name", " ".join(kv.value.split()), kv)

    for name in _MONEY_FIELDS:
        kv = found[name]
        amount = parse_money(kv.value) if kv else None
        if amount is not None:
            record(name, amount, kv)
        elif name in REQUIRED and kv and kv.value:
            warnings.append(f"{name}: could not read an amount from {kv.value!r}")

    if (kv := found["box13_retirement_plan"]) and kv.selected is not None:
        record("box13_retirement_plan", kv.selected, kv)

    # Box 15: the key is just "State"; on state-less W-2s Textract may pair it with the
    # printed box number "15", so only accept a real 2-letter code.
    state_kv = next((kv for kv in kvs if kv.label in ("state", "15 state")), None)
    if state_kv and state_kv.value:
        if _STATE_CODE.match(state_kv.value.upper()):
            record("box15_state", state_kv.value.upper(), state_kv)
        else:
            warnings.append(f"box 15: ignored {state_kv.value!r}, not a state code")
    if "box15_state" in values and (kv := found["box15_state_id"]) and kv.value:
        record("box15_state_id", kv.value, kv)

    entries, box12_conf = _box12_entries(doc.lines, warnings)
    if entries:
        record("box12", entries, confidence=box12_conf)

    missing = [name for name in REQUIRED if name not in values]
    if missing:
        raise IncompleteExtractionError("W-2", missing, values, warnings)
    try:
        document = W2(**values)
    except ValidationError as exc:
        bad = sorted({str(err["loc"][0]) for err in exc.errors() if err["loc"]})
        raise IncompleteExtractionError("W-2", bad, values, [*warnings, str(exc)]) from exc

    return ExtractionResult(
        form_type=FormType.W2, document=document, fields=meta, warnings=warnings
    )
