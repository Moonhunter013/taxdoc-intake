from __future__ import annotations


class IncompleteExtractionError(Exception):
    """Required fields could not be read from the document.

    Carries what *was* read, so the API can show a partial result and ask a human to fill in
    the gaps rather than failing with no information.
    """

    def __init__(self, form_type: str, missing: list[str], partial: dict, warnings: list[str]):
        self.form_type = form_type
        self.missing = missing
        self.partial = partial
        self.warnings = warnings
        super().__init__(f"{form_type}: could not read required field(s): {', '.join(missing)}")
