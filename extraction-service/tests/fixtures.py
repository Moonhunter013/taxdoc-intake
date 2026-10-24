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
