"""A thin, read-only view over an AWS Textract AnalyzeDocument (FORMS) response.

Textract returns a flat list of blocks linked by IDs:
  * KEY_VALUE_SET blocks come in pairs: a KEY (the printed label) whose VALUE relationship
    points at a VALUE block (what was filled in). Both link to their WORD children.
  * SELECTION_ELEMENT blocks are checkboxes (SELECTED / NOT_SELECTED).
  * LINE blocks are plain lines of text with their position on the page.
Geometry is relative to the page: 0.0 = top/left edge, 1.0 = bottom/right edge.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from functools import cached_property

from app.extraction.normalize import normalize_label


@dataclass(frozen=True)
class Box:
    top: float
    left: float
    width: float
    height: float

    @classmethod
    def of(cls, block: dict) -> Box:
        bb = block["Geometry"]["BoundingBox"]
        return cls(bb["Top"], bb["Left"], bb["Width"], bb["Height"])


@dataclass(frozen=True)
class Line:
    text: str
    confidence: float
    box: Box


@dataclass(frozen=True)
class KeyValue:
    label: str  # normalized, for matching
    raw_label: str  # as printed, for messages
    value: str  # words joined with spaces
    value_lines: list[str] = field(default_factory=list)  # the value split into visual lines
    selected: bool | None = None  # checkbox state, if the value is a checkbox
    confidence: float = 0.0  # min(key, value) confidence
    box: Box | None = None  # position of the key


class TextractDocument:
    def __init__(self, response: dict):
        self._blocks: dict[str, dict] = {b["Id"]: b for b in response.get("Blocks", [])}

    def _children(self, block: dict, rel_type: str = "CHILD") -> list[dict]:
        ids = [i for r in block.get("Relationships", []) if r["Type"] == rel_type for i in r["Ids"]]
        return [self._blocks[i] for i in ids if i in self._blocks]

    def _words(self, block: dict) -> list[dict]:
        return [c for c in self._children(block) if c["BlockType"] == "WORD"]

    @staticmethod
    def _group_lines(words: list[dict]) -> list[str]:
        """Group words into visual lines: a word starts a new line when its top is more than
        half a word-height below the current line's top."""
        lines: list[list[dict]] = []
        for word in sorted(words, key=lambda w: Box.of(w).top):
            box = Box.of(word)
            if lines and box.top - Box.of(lines[-1][0]).top < box.height / 2:
                lines[-1].append(word)
            else:
                lines.append([word])
        return [
            " ".join(w["Text"] for w in sorted(ln, key=lambda w: Box.of(w).left)) for ln in lines
        ]

    @cached_property
    def key_values(self) -> list[KeyValue]:
        pairs = []
        for block in self._blocks.values():
            if block["BlockType"] != "KEY_VALUE_SET" or "KEY" not in block.get("EntityTypes", []):
                continue
            raw_label = " ".join(w["Text"] for w in self._words(block))
            values = self._children(block, "VALUE")
            value_block = values[0] if values else None
            words = self._words(value_block) if value_block else []
            selections = [
                c
                for c in (self._children(value_block) if value_block else [])
                if c["BlockType"] == "SELECTION_ELEMENT"
            ]
            confidences = [block["Confidence"]] + (
                [value_block["Confidence"]] if value_block else []
            )
            pairs.append(
                KeyValue(
                    label=normalize_label(raw_label),
                    raw_label=raw_label,
                    value=" ".join(w["Text"] for w in words),
                    value_lines=self._group_lines(words),
                    selected=(selections[0]["SelectionStatus"] == "SELECTED")
                    if selections
                    else None,
                    confidence=min(confidences),
                    box=Box.of(block),
                )
            )
        return pairs

    def line_below(self, label: re.Pattern[str], max_gap: float = 0.05) -> Line | None:
        """The first text line directly under a printed label, in the same column.

        Fallback for when Textract's key-value detection drops a value that its plain text
        recognition still read correctly (see the 1099-NEC state row).
        """
        header = next((ln for ln in self.lines if label.match(ln.text)), None)
        if header is None:
            return None
        below = [
            ln
            for ln in self.lines
            if abs(ln.box.left - header.box.left) < 0.02
            and 0 < ln.box.top - (header.box.top + header.box.height) < max_gap
        ]
        return min(below, key=lambda ln: ln.box.top, default=None)

    @cached_property
    def lines(self) -> list[Line]:
        return [
            Line(b["Text"], b["Confidence"], Box.of(b))
            for b in self._blocks.values()
            if b["BlockType"] == "LINE"
        ]
