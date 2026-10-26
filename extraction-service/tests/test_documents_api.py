"""Tests for POST /api/v1/documents with S3 and Textract replaced by fakes."""

import io
import re
from uuid import UUID

import pytest
from botocore.exceptions import ClientError
from fastapi.testclient import TestClient
from pypdf import PdfWriter

from app.api.dependencies import (
    get_repository,
    get_s3_client,
    get_settings,
    get_textract_client,
)
from app.config import Settings
from app.db.repository import InMemoryDocumentRepository
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


TEST_SETTINGS = Settings(s3_bucket="test-bucket", tin_hmac_key="test-hmac-key")


@pytest.fixture
def repo():
    return InMemoryDocumentRepository()


@pytest.fixture
def api(repo):
    """Yields (client, s3, set_textract) with AWS and the database replaced by fakes."""
    s3 = FakeS3()
    state = {"textract": FakeTextract(load_fixture("client-01_w2"))}
    app.dependency_overrides[get_s3_client] = lambda: s3
    app.dependency_overrides[get_textract_client] = lambda: state["textract"]
    app.dependency_overrides[get_settings] = lambda: TEST_SETTINGS
    app.dependency_overrides[get_repository] = lambda: repo

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


# --- persistence, GET and PATCH -------------------------------------------------------------


def test_upload_is_stored_with_tins_minimized(api, repo):
    client, _, _ = api
    body = upload(client, pdf_bytes()).json()
    stored = repo.get(UUID(body["document_id"]))
    assert stored.values["employee_ssn"] == "***-**-2201"
    assert "000-14-2201" not in stored.model_dump_json()
    assert len(stored.taxpayer_tin_hash) == 64
    assert stored.taxpayer_tin_last4 == "2201"


def test_get_returns_the_stored_document(api):
    client, _, _ = api
    created = upload(client, pdf_bytes()).json()
    fetched = client.get(f"{URL}/{created['document_id']}").json()
    assert fetched == created


def test_get_unknown_document_is_404(api):
    client, _, _ = api
    assert client.get(f"{URL}/00000000-0000-0000-0000-000000000000").status_code == 404


def test_patch_corrects_a_value_and_records_who(api):
    client, _, _ = api
    doc_id = upload(client, pdf_bytes()).json()["document_id"]
    resp = client.patch(
        f"{URL}/{doc_id}/fields/box17_state_tax",
        json={"value": "8900.00", "corrected_by": "paul@example.com"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["document"]["box17_state_tax"] == "8900.00"
    assert body["fields"]["box17_state_tax"]["corrected_by"] == "paul@example.com"
    assert body["fields"]["box17_state_tax"]["corrected_at"] is not None
    # persisted
    assert client.get(f"{URL}/{doc_id}").json()["document"]["box17_state_tax"] == "8900.00"


def test_patch_reruns_consistency_rules(api):
    client, _, _ = api
    doc_id = upload(client, pdf_bytes()).json()["document_id"]
    bad = client.patch(
        f"{URL}/{doc_id}/fields/box4_ss_tax", json={"value": "1439.00", "corrected_by": "paul"}
    ).json()
    assert bad["needs_review"] is True
    assert any("6.2% of box 3" in r for r in bad["review_reasons"])
    fixed = client.patch(
        f"{URL}/{doc_id}/fields/box4_ss_tax", json={"value": "11439.00", "corrected_by": "paul"}
    ).json()
    assert fixed["needs_review"] is False
    assert fixed["review_reasons"] == []


def test_patch_returns_the_reason_on_the_field_it_concerns(api):
    """#26: the review screen shows each reason next to its box, from fields[name]."""
    client, _, _ = api
    doc_id = upload(client, pdf_bytes()).json()["document_id"]
    bad = client.patch(
        f"{URL}/{doc_id}/fields/box4_ss_tax", json={"value": "1439.00", "corrected_by": "paul"}
    ).json()
    [reason] = bad["fields"]["box4_ss_tax"]["review_reasons"]
    assert "6.2% of box 3" in reason
    assert reason in bad["review_reasons"]  # the document-level list is unchanged
    assert bad["fields"]["box3_ss_wages"]["review_reasons"] == []  # only the box it's about

    fixed = client.patch(
        f"{URL}/{doc_id}/fields/box4_ss_tax", json={"value": "11439.00", "corrected_by": "paul"}
    ).json()
    assert fixed["fields"]["box4_ss_tax"]["review_reasons"] == []


def test_patch_rejects_invalid_value_without_echoing_it(api):
    client, _, _ = api
    doc_id = upload(client, pdf_bytes()).json()["document_id"]
    resp = client.patch(
        f"{URL}/{doc_id}/fields/employee_ssn", json={"value": "999-99", "corrected_by": "paul"}
    )
    assert resp.status_code == 422
    assert "999-99" not in resp.text


def test_patch_tin_stores_masked_value_and_new_hash(api, repo):
    client, _, _ = api
    doc_id = upload(client, pdf_bytes()).json()["document_id"]
    before = repo.get(UUID(doc_id)).taxpayer_tin_hash
    body = client.patch(
        f"{URL}/{doc_id}/fields/employee_ssn",
        json={"value": "000-14-9999", "corrected_by": "paul"},
    ).json()
    assert body["document"]["employee_ssn"] == "***-**-9999"
    after = repo.get(UUID(doc_id))
    assert after.taxpayer_tin_hash != before
    assert "000-14-9999" not in after.model_dump_json()


def test_patch_unknown_field_is_404(api):
    client, _, _ = api
    doc_id = upload(client, pdf_bytes()).json()["document_id"]
    resp = client.patch(
        f"{URL}/{doc_id}/fields/box99_magic", json={"value": "1", "corrected_by": "paul"}
    )
    assert resp.status_code == 404


def test_missing_hmac_key_refuses_to_store(api, repo):
    client, _, _ = api
    app.dependency_overrides[get_settings] = lambda: Settings(s3_bucket="b", tin_hmac_key="")
    resp = upload(client, pdf_bytes())
    assert resp.status_code == 500
    assert resp.json()["detail"] == "Server configuration error"


# --- clients (#18) ----------------------------------------------------------------------------


def upload_for(client, set_textract, fixture: str, client_ref: str):
    set_textract(FakeTextract(load_fixture(fixture)))
    resp = upload(client, pdf_bytes(), client_ref=client_ref)
    assert resp.status_code == 201, resp.text
    return resp.json()


def test_client_ref_is_stored_and_returned(api):
    client, _, set_textract = api
    body = upload_for(client, set_textract, "client-01_w2", "client-01")
    assert body["client_ref"] == "client-01"
    assert client.get(f"{URL}/{body['document_id']}").json()["client_ref"] == "client-01"


def test_lists_a_clients_documents_newest_first(api):
    client, _, set_textract = api
    first = upload_for(client, set_textract, "client-10_w2_1", "client-10")
    second = upload_for(client, set_textract, "client-10_w2_2", "client-10")
    upload_for(client, set_textract, "client-01_w2", "client-01")  # another client

    body = client.get("/api/v1/clients/client-10/documents").json()
    assert [d["document_id"] for d in body["documents"]] == [
        second["document_id"],
        first["document_id"],
    ]
    # Same employee on both W-2s: one taxpayer, no warning
    assert len(body["taxpayer_groups"]) == 1
    assert body["warnings"] == []


def test_w2_and_1099_for_the_same_person_are_one_taxpayer(api):
    client, _, set_textract = api
    upload_for(client, set_textract, "client-06_w2", "client-06")  # employee SSN
    upload_for(client, set_textract, "client-06_1099nec", "client-06")  # recipient TIN
    body = client.get("/api/v1/clients/client-06/documents").json()
    assert len(body["taxpayer_groups"]) == 1


def test_documents_from_different_taxpayers_are_flagged(api):
    client, _, set_textract = api
    mine = upload_for(client, set_textract, "client-01_w2", "client-01")
    misfiled = upload_for(client, set_textract, "client-02_1099nec_1", "client-01")
    body = client.get("/api/v1/clients/client-01/documents").json()
    assert sorted(map(sorted, body["taxpayer_groups"])) == sorted(
        [[mine["document_id"]], [misfiled["document_id"]]]
    )
    assert "2 different taxpayers" in body["warnings"][0]
    assert "000-" not in str(body)  # no full SSNs anywhere


def test_unknown_client_returns_empty_list(api):
    client, _, _ = api
    body = client.get("/api/v1/clients/new-client/documents").json()
    assert body == {
        "client_ref": "new-client",
        "documents": [],
        "taxpayer_groups": [],
        "warnings": [],
    }


@pytest.mark.parametrize("bad", ["../etc", "has space", "x" * 65, "-leading-dash"])
def test_invalid_client_ref_is_rejected(api, bad):
    client, s3, _ = api
    resp = upload(client, pdf_bytes(), client_ref=bad)
    assert resp.status_code == 422
    assert s3.objects == {}  # rejected before anything is stored
