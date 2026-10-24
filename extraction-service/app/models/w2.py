"""Form W-2, Wage and Tax Statement.

Field names match `samples/ground-truth/*.json`, so extraction output can be compared to
the expected values directly. Only the boxes this project uses are modelled; boxes 7-11
and 14 can be added when a planning strategy needs them.
"""

from __future__ import annotations

from decimal import Decimal

from pydantic import Field, model_validator

from app.models.common import EIN, SSN, Box12Entry, Money, StateCode, StrictModel, mask_tin


class W2(StrictModel):
    employer_name: str = Field(min_length=1)
    employer_ein: EIN = Field(repr=False)
    employer_address: str
    employee_name: str | None = None
    employee_ssn: SSN | None = Field(default=None, repr=False)

    box1_wages: Money
    box2_federal_withholding: Money
    box3_ss_wages: Money
    box4_ss_tax: Money
    box5_medicare_wages: Money
    box6_medicare_tax: Money
    box12: list[Box12Entry] = Field(default_factory=list, max_length=4)
    box13_retirement_plan: bool = False

    # One state row. Real W-2s can have two; add a list when a client needs it.
    box15_state: StateCode | None = None
    box15_state_id: str | None = None
    box16_state_wages: Money = Decimal("0.00")
    box17_state_tax: Money = Decimal("0.00")

    @model_validator(mode="after")
    def _state_fields_consistent(self) -> W2:
        """State wages/tax without a state code means the state row was misread."""
        if self.box15_state is None and (self.box16_state_wages or self.box17_state_tax):
            raise ValueError("box 16/17 amounts present but box 15 state is missing")
        return self

    @property
    def masked_ein(self) -> str:
        return mask_tin(self.employer_ein)

    @property
    def masked_ssn(self) -> str:
        return mask_tin(self.employee_ssn)
