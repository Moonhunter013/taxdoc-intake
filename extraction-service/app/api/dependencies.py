"""FastAPI dependencies for AWS clients.

Endpoints ask for clients through `Depends(...)` instead of creating them, so tests can swap in
fakes with `app.dependency_overrides` and never touch AWS.
"""

from __future__ import annotations

from functools import lru_cache

import boto3

from app.config import Settings, settings


def get_settings() -> Settings:
    return settings


@lru_cache
def _session() -> boto3.Session:
    # Created once and reused: building a session reads credentials from disk/environment.
    return boto3.Session(profile_name=settings.aws_profile, region_name=settings.aws_region)


def get_s3_client():
    return _session().client("s3")


def get_textract_client():
    return _session().client("textract")
