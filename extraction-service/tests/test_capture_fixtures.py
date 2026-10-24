"""Tests for scripts/capture_fixtures.py, with AWS replaced by fakes."""

import importlib.util
import json
import sys
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "capture_fixtures.py"
spec = importlib.util.spec_from_file_location("capture_fixtures", SCRIPT)
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class FakeS3:
    def __init__(self):
        self.uploads = []

    def upload_file(self, filename, bucket, key):
        self.uploads.append((Path(filename).name, bucket, key))


class FakeTextract:
    def analyze_document(self, Document, FeatureTypes):  # noqa: N803 (boto3 casing)
        assert FeatureTypes == ["FORMS"]
        return {
            "DocumentMetadata": {"Pages": 1},
            "Blocks": [{"BlockType": "PAGE", "Text": "Employee’s SSN"}],
            "ResponseMetadata": {"RequestId": "changes-every-call"},
        }


@pytest.fixture
def fake_aws(monkeypatch):
    s3, textract = FakeS3(), FakeTextract()

    class FakeSession:
        def __init__(self, profile_name=None, region_name=None):
            pass

        def client(self, name):
            return {"s3": s3, "textract": textract}[name]

    monkeypatch.setattr(capture.boto3, "Session", FakeSession)
    return s3


def run(monkeypatch, *args):
    monkeypatch.setattr(sys, "argv", ["capture_fixtures.py", *args])
    return capture.main()


def make_forms(tmp_path, *names):
    forms = tmp_path / "forms"
    forms.mkdir()
    for name in names:
        (forms / name).write_bytes(b"%PDF-1.7 fake")
    return forms


def test_captures_and_strips_response_metadata(tmp_path, monkeypatch, fake_aws):
    forms = make_forms(tmp_path, "client-01_w2.pdf")
    out = tmp_path / "out"
    code = run(monkeypatch, "--forms-dir", str(forms), "--out-dir", str(out), "--bucket", "b")

    assert code == 0
    assert fake_aws.uploads == [("client-01_w2.pdf", "b", "fixtures/client-01_w2.pdf")]
    data = json.loads((out / "client-01_w2.json").read_text(encoding="utf-8"))
    assert "ResponseMetadata" not in data
    assert data["Blocks"][0]["Text"] == "Employee’s SSN"  # non-ASCII survives


def test_skips_existing_fixtures_unless_forced(tmp_path, monkeypatch, fake_aws):
    forms = make_forms(tmp_path, "a.pdf", "b.pdf")
    out = tmp_path / "out"
    out.mkdir()
    (out / "a.json").write_text("{}", encoding="utf-8")

    run(monkeypatch, "--forms-dir", str(forms), "--out-dir", str(out), "--bucket", "b")
    assert [u[0] for u in fake_aws.uploads] == ["b.pdf"]

    run(monkeypatch, "--forms-dir", str(forms), "--out-dir", str(out), "--bucket", "b", "--force")
    assert [u[0] for u in fake_aws.uploads] == ["b.pdf", "a.pdf", "b.pdf"]


def test_missing_bucket_is_an_error(tmp_path, monkeypatch, fake_aws):
    forms = make_forms(tmp_path, "a.pdf")
    assert run(monkeypatch, "--forms-dir", str(forms), "--bucket", "") == 2
