from decimal import Decimal

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict

# Fields Textract consistently reports at ~72-80% confidence even when correct (the state row
# on both forms; see docs/adr/0004-extraction-confidence-and-review.md for the measurements).
_STATE_FIELDS_THRESHOLD = 70.0


class Settings(BaseSettings):
    """Runtime configuration, read from environment variables (see ../.env.example).

    Values come from, in priority order: real environment variables, then
    `extraction-service/.env`, then the repo-root `.env`. Dict values can be overridden with
    JSON, e.g. EXTRACTION_SS_WAGE_BASE_BY_YEAR='{"2026": 184500, "2027": 190000}'.
    """

    model_config = SettingsConfigDict(
        env_prefix="EXTRACTION_",
        env_file=("../.env", ".env"),  # later files win
        extra="ignore",
    )

    database_url: str = "mysql+pymysql://taxdoc:taxdoc@localhost:3306/taxdoc"
    s3_bucket: str = ""
    aws_region: str = "us-east-1"
    # Local dev uses a named CLI profile (AWS_PROFILE in the repo-root .env). In AWS (ECS) leave
    # it unset so boto3 uses the task's IAM role.
    aws_profile: str | None = Field(
        default=None, validation_alias=AliasChoices("EXTRACTION_AWS_PROFILE", "AWS_PROFILE")
    )

    # --- Uploads (issue 7) ---
    max_upload_bytes: int = 5 * 1024 * 1024

    # --- Human review (issue 6) ---
    review_default_threshold: float = 90.0
    review_field_thresholds: dict[str, float] = {
        "box15_state": _STATE_FIELDS_THRESHOLD,
        "box15_state_id": _STATE_FIELDS_THRESHOLD,
        "box16_state_wages": _STATE_FIELDS_THRESHOLD,
        "box17_state_tax": _STATE_FIELDS_THRESHOLD,
        "box6_state": _STATE_FIELDS_THRESHOLD,
        "box6_state_id": _STATE_FIELDS_THRESHOLD,
        # Display only (clients are matched by SSN, which keeps the 90% default). Measured
        # 86-90% on correct values; 80% still catches a genuinely poor read.
        "employee_name": 80.0,
    }
    # Dollar tolerance for consistency checks (rounding on real W-2s).
    review_tolerance: Decimal = Decimal("1.00")

    # --- Tax-year constants ---
    default_tax_year: int = 2026
    ss_wage_base_by_year: dict[int, Decimal] = {2026: Decimal("184500")}
    ss_tax_rate: Decimal = Decimal("0.062")
    medicare_tax_rate: Decimal = Decimal("0.0145")

    def threshold_for(self, field: str) -> float:
        return self.review_field_thresholds.get(field, self.review_default_threshold)


settings = Settings()
