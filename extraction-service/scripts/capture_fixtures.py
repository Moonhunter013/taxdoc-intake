"""Capture AWS Textract responses for the sample forms, so tests never call AWS.

For each PDF in samples/forms/ this script:
  1. uploads it to the dev bucket under fixtures/<name>.pdf
  2. calls Textract AnalyzeDocument with the FORMS feature
  3. writes the response to samples/textract-fixtures/<name>.json (UTF-8)

Run from the extraction-service folder:
    uv run python scripts/capture_fixtures.py            # only forms without a fixture
    uv run python scripts/capture_fixtures.py --force    # re-capture everything

Credentials come from the normal AWS chain; by default the `taxdoc` CLI profile.
The bucket comes from --bucket or EXTRACTION_S3_BUCKET (repo-root .env is read).

Cost: one Textract page per form (15 total), inside the free tier.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import boto3
from botocore.exceptions import BotoCoreError, ClientError

# Allow `from app.config import settings` when run as a script from extraction-service/.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.config import settings  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_FORMS = REPO_ROOT / "samples" / "forms"
DEFAULT_OUT = REPO_ROOT / "samples" / "textract-fixtures"


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--forms-dir", type=Path, default=DEFAULT_FORMS)
    p.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    p.add_argument("--bucket", default=settings.s3_bucket, help="defaults to EXTRACTION_S3_BUCKET")
    p.add_argument("--profile", default=os.environ.get("AWS_PROFILE", "taxdoc"))
    p.add_argument("--region", default=settings.aws_region)
    p.add_argument("--prefix", default="fixtures/", help="S3 key prefix for uploaded forms")
    p.add_argument("--force", action="store_true", help="re-capture even if a fixture exists")
    return p.parse_args()


def clean_response(response: dict) -> dict:
    """Drop ResponseMetadata: it holds request IDs and HTTP headers that change on every
    call, which would make re-captured fixtures differ for no meaningful reason."""
    return {k: v for k, v in response.items() if k != "ResponseMetadata"}


def main() -> int:
    args = parse_args()
    if not args.bucket:
        print("No bucket: pass --bucket or set EXTRACTION_S3_BUCKET in .env", file=sys.stderr)
        return 2

    pdfs = sorted(args.forms_dir.glob("*.pdf"))
    if not pdfs:
        print(f"No PDFs found in {args.forms_dir}", file=sys.stderr)
        return 2

    session = boto3.Session(profile_name=args.profile, region_name=args.region)
    s3 = session.client("s3")
    textract = session.client("textract")
    args.out_dir.mkdir(parents=True, exist_ok=True)

    captured, skipped, failed = 0, 0, []
    for pdf in pdfs:
        out = args.out_dir / f"{pdf.stem}.json"
        if out.exists() and not args.force:
            skipped += 1
            continue

        key = f"{args.prefix}{pdf.name}"
        try:
            s3.upload_file(str(pdf), args.bucket, key)
            response = textract.analyze_document(
                Document={"S3Object": {"Bucket": args.bucket, "Name": key}},
                FeatureTypes=["FORMS"],
            )
        except (ClientError, BotoCoreError) as exc:
            failed.append(pdf.name)
            print(f"FAILED {pdf.name}: {exc}", file=sys.stderr)
            continue

        data = clean_response(response)
        out.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        captured += 1
        print(f"captured {pdf.name}: {len(data.get('Blocks', []))} blocks")

    print(f"\n{captured} captured, {skipped} skipped (already present), {len(failed)} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
