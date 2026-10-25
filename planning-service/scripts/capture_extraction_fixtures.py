"""Capture real extraction-service responses as planning-service test fixtures.

Runs the extraction app in-process with S3, Textract and the database replaced by the same fakes
its own tests use, uploads the captured Textract output for a client, and saves the response of
GET /api/v1/clients/{ref}/documents. The Java tests then parse exactly what extraction sends.

Run from extraction-service (PowerShell):
    cd extraction-service; $env:PYTHONPATH = "."; uv run python ..\\planning-service\\scripts\\capture_extraction_fixtures.py; cd ..
"""

from __future__ import annotations

import json
from pathlib import Path

from fastapi.testclient import TestClient

from app.api.dependencies import get_repository, get_s3_client, get_settings, get_textract_client
from app.db.repository import InMemoryDocumentRepository
from app.main import app
from tests.fixtures import load_fixture
from tests.test_documents_api import TEST_SETTINGS, FakeS3, FakeTextract, pdf_bytes

CLIENTS = {
    "client-03": ["client-03_w2", "client-03_1099nec"],
    "client-10": ["client-10_w2_1", "client-10_w2_2"],
}
OUT = Path(__file__).resolve().parents[1] / "src" / "test" / "resources" / "extraction"


def capture(client_ref: str, fixtures: list[str]) -> dict:
    textract = {"fake": None}
    app.dependency_overrides[get_s3_client] = FakeS3
    app.dependency_overrides[get_textract_client] = lambda: textract["fake"]
    app.dependency_overrides[get_settings] = lambda: TEST_SETTINGS
    repo = InMemoryDocumentRepository()
    app.dependency_overrides[get_repository] = lambda: repo
    try:
        client = TestClient(app)
        for name in fixtures:
            textract["fake"] = FakeTextract(load_fixture(name))
            files = {"file": ("form.pdf", pdf_bytes(), "application/pdf")}
            resp = client.post("/api/v1/documents", files=files, data={"client_ref": client_ref})
            resp.raise_for_status()
        resp = client.get(f"/api/v1/clients/{client_ref}/documents")
        resp.raise_for_status()
        return resp.json()
    finally:
        app.dependency_overrides.clear()


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for client_ref, fixtures in CLIENTS.items():
        path = OUT / f"{client_ref}.json"
        # Write UTF-8 from Python, not via PowerShell redirection (see AI_LOG 2026-10-24).
        # newline="\n": LF on every OS, so fixtures are byte-identical wherever they're captured.
        body = json.dumps(capture(client_ref, fixtures), indent=2) + "\n"
        path.write_text(body, encoding="utf-8", newline="\n")
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
