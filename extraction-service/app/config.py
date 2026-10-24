from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration, read from environment variables (see ../.env.example).

    Values come from, in priority order: real environment variables, then
    `extraction-service/.env`, then the repo-root `.env`.
    """

    model_config = SettingsConfigDict(
        env_prefix="EXTRACTION_",
        env_file=("../.env", ".env"),  # later files win
        extra="ignore",
    )

    database_url: str = "mysql+pymysql://taxdoc:taxdoc@localhost:3306/taxdoc"
    s3_bucket: str = ""
    aws_region: str = "us-east-1"


settings = Settings()
