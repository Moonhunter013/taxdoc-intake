from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration, read from environment variables (see ../.env.example)."""

    model_config = SettingsConfigDict(env_prefix="EXTRACTION_", env_file=".env", extra="ignore")

    database_url: str = "mysql+pymysql://taxdoc:taxdoc@localhost:3306/taxdoc"
    s3_bucket: str = "taxdoc-intake-dev-uploads"
    aws_region: str = "us-east-1"


settings = Settings()
