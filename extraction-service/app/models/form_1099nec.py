"""Form 1099-NEC, Nonemployee Compensation (Rev. December 2026 layout).

The 2026 revision splits box 1 into 1a-1d; box 1a is nonemployee compensation.
Field names match `samples/ground-truth/*.json`.
"""

from __future__ import annotations

from decimal import Decimal

from pydantic import Field, model_validator

from app.models.common import TIN, Money, StateCode, StrictModel, mask_tin


class Form1099NEC(StrictModel):
    payer_name: str = Field(min_length=1)
    payer_tin: TIN = Field(repr=False)
    payer_address: str
    recipient_name: str | None = None
    recipient_tin: TIN | None = Field(default=None, repr=False)

    box1a_nonemployee_comp: Money
    box4_federal_withholding: Money = Decimal("0")
    box6_state: StateCode | None = None
    box6_state_id: str | None = None
    box7_state_income: Money = Decimal("0")

    @model_validator(mode="after")
    def _state_fields_consistent(self) -> Form1099NEC:
        if self.box6_state is None and self.box7_state_income:
            raise ValueError("box 7 state income present but box 6 state is missing")
        return self

    @property
    def masked_payer_tin(self) -> str:
        return mask_tin(self.payer_tin)
