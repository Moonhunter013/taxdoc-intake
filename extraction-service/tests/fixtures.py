"""Helpers for loading saved Textract responses and ground truth in tests."""

from __future__ import annotations

import json
from pathlib import Path

SAMPLES = Path(__file__).resolve().parents[2] / "samples"
FIXTURES = SAMPLES / "textract-fixtures"
GROUND_TRUTH = SAMPLES / "ground-truth"


def load_fixture(name: str) -> dict:
    return json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8"))


def expected_document(name: str) -> dict:
    """Ground-truth values for e.g. 'client-10_w2_2' (the file 'client-10_w2_2.pdf')."""
    client = name.split("_")[0]
    truth = json.loads((GROUND_TRUTH / f"{client}.json").read_text(encoding="utf-8"))
    doc = next(d for d in truth["documents"] if d["file"] == f"{name}.pdf")
    return {k: v for k, v in doc.items() if k not in ("file", "form")}


def fixture_names(pattern: str) -> list[str]:
    return sorted(p.stem for p in FIXTURES.glob(f"{pattern}.json"))


def without_field(response: dict, label_start: str) -> dict:
    """Copy of a Textract response with one key-value pair removed (simulates a missed field).

    `label_start` is matched against the start of the printed label, e.g. "1 Wages".
    """
    blocks = {b["Id"]: b for b in response["Blocks"]}

    def words(block):
        ids = [i for r in block.get("Relationships", []) if r["Type"] == "CHILD" for i in r["Ids"]]
        return " ".join(blocks[i].get("Text", "") for i in ids)

    drop = set()
    for block in response["Blocks"]:
        if block["BlockType"] == "KEY_VALUE_SET" and words(block).startswith(label_start):
            drop.add(block["Id"])
            for rel in block.get("Relationships", []):
                if rel["Type"] == "VALUE":
                    drop.update(rel["Ids"])
    assert drop, f"no key-value pair starting with {label_start!r}"
    return {**response, "Blocks": [b for b in response["Blocks"] if b["Id"] not in drop]}
