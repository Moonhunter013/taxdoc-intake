"""Tests for POST /api/v1/documents with S3 and Textract replaced by fakes."""

import io
import re

import pytest
from botocore.exceptions import ClientError
from fastapi.testclient import TestClient
from pypdf import PdfWriter

from app.api.dependencies import get_s3_client, get_settings, get_textract_client
from app.config import Settings
from app.main import app
from tests.fixtures import SAMPLES, load_fixture, without_field

URL = "/api/v1/documents"


class FakeS3:
    def __init__(self):
        self.objects = {}

    def put_object(self, Bucket, Key, Body, ContentType):  # noqa: N803 (boto3 casing)
        self.objects[(Bucket, Key)] = Body


class FakeTextract:
    def __init__(self, response=None, error=None):
        self.response, self.error, self.calls = response, error, []

    def analyze_document(self, Document, FeatureTypes):  # noqa: N803
        self.calls.append(Document)
        if self.error:
            raise self.error
        return self.response


@pytest.fixture
def api():
    """Yields (client, s3, set_textract) with AWS dependencies overridden."""
    s3 = FakeS3()
    state = {"textract": FakeTextract(load_fixture("client-01_w2"))}
    app.dependency_overrides[get_s3_client] = lambda: s3
    app.dependency_overrides[get_textract_client] = lambda: state["textract"]
    app.dependency_overrides[get_settings] = lambda: Settings(s3_bucket="test-bucket")

    def set_textract(fake):
        state["textract"] = fake

    yield TestClient(app), s3, set_textract
    app.dependency_overrides.clear()


def pdf_bytes(pages: int = 1) -> bytes:
    writer = PdfWriter()
    for _ in range(pages):
        writer.add_blank_page(width=612, height=396)
    buf = io.BytesIO()
    writer.write(buf)
    return buf.getvalue()


def upload(client, data: bytes, filename="form.pdf", **form):
    return client.post(URL, files={"file": (filename, data, "application/pdf")}, data=form)


def test_uploads_real_sample_form_and_returns_extracted_w2(api):
    client, s3, _ = api
    data = (SAMPLES / "forms" / "client-01_w2.pdf").read_bytes()
    resp = upload(client, data)

    assert resp.status_code == 201
    body = resp.json()
    assert body["form_type"] == "W-2"
    assert body["needs_review"] is False
    assert body["document"]["box1_wages"] == "203000.00"
    assert body["document"]["employer_name"] == "Halcyon Robotics Inc"
    assert body["fields"]["box1_wages"]["confidence"] > 90
    # Stored once, under a server-generated key in the configured bucket
    [(bucket, key)] = s3.objects
    assert bucket == "test-bucket"
    assert re.fullmatch(r"documents/[0-9a-f-]{36}\.pdf", key)
    assert key == f"documents/{body['document_id']}.pdf"


def test_tax_ids_are_masked_in_the_response(api):
    client, _, _ = api
    body = upload(client, pdf_bytes()).json()
    assert body["document"]["employee_ssn"] == "***-**-2201"
    assert body["document"]["employer_ein"] == "**-***2201"
    assert "000-14-2201" not in str(body)


def test_client_filename_is_never_used_as_the_s3_key(api):
    client, s3, _ = api
    upload(client, pdf_bytes(), filename="../../etc/passwd.pdf")
    [(_, key)] = s3.objects
    assert "passwd" not in key and ".." not in key


def test_routes_1099nec_to_its_mapper(api):
    client, _, set_textract = api
    set_textract(FakeTextract(load_fixture("client-09_1099nec")))
    body = upload(client, pdf_bytes()).json()
    assert body["form_type"] == "1099-NEC"
    assert body["document"]["box1a_nonemployee_comp"] == "310000.00"
    assert body["document"]["payer_tin"] == "**-***1357"


def test_rejects_non_pdf_even_with_pdf_name_and_type(api):
    client, s3, _ = api
    resp = upload(client, b"GIF89a not a pdf", filename="w2.pdf")
    assert resp.status_code == 415
    assert s3.objects == {}  # nothing stored


def test_rejects_files_over_the_size_limit(api):
    client, s3, _ = api
    too_big = b"%PDF-1.7\n" + b"0" * (5 * 1024 * 1024)
    assert upload(client, too_big).status_code == 413
    assert s3.objects == {}


def test_rejects_multi_page_pdfs(api):
    client, _, _ = api
    resp = upload(client, pdf_bytes(pages=3))
    assert resp.status_code == 422
    assert "3 pages" in resp.json()["detail"]


def test_rejects_corrupt_pdf(api):
    client, _, _ = api
    assert upload(client, b"%PDF-1.7 garbage").status_code == 422


def test_unrecognised_form_returns_422(api):
    client, _, set_textract = api
    set_textract(FakeTextract({"Blocks": []}))
    resp = upload(client, pdf_bytes())
    assert resp.status_code == 422
    assert "Not a W-2 or 1099-NEC" in resp.json()["detail"]


def test_incomplete_extraction_returns_missing_fields_and_masked_partial(api):
    client, _, set_textract = api
    set_textract(FakeTextract(without_field(load_fixture("client-01_w2"), "1 Wages")))
    resp = upload(client, pdf_bytes())
    assert resp.status_code == 422
    body = resp.json()
    assert body["missing_fields"] == ["box1_wages"]
    assert body["partial"]["box2_federal_withholding"] == "42630.00"
    assert body["partial"]["employee_ssn"] == "***-**-2201"


def test_aws_failure_returns_502_without_leaking_details(api):
    client, _, set_textract = api
    error = ClientError(
        {"Error": {"Code": "AccessDenied", "Message": "secret arn"}}, "AnalyzeDocument"
    )
    set_textract(FakeTextract(error=error))
    resp = upload(client, pdf_bytes())
    assert resp.status_code == 502
    assert "secret" not in resp.text and "AccessDenied" not in resp.text


def test_tax_year_is_passed_to_review(api):
    client, _, _ = api
    body = upload(client, pdf_bytes(), tax_year="2031").json()
    assert body["needs_review"] is True
    assert "no Social Security wage base configured for 2031" in body["review_reasons"]


def test_all_money_values_use_two_decimal_places(api):
    client, _, set_textract = api
    set_textract(FakeTextract(load_fixture("client-06_w2")))  # no state row: defaults used
    doc = upload(client, pdf_bytes()).json()["document"]
    assert doc["box16_state_wages"] == "0.00"
    assert doc["box17_state_tax"] == "0.00"
