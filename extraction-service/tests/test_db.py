"""SqlDocumentRepository and the Alembic migration, against a real MySQL 8.4.

A throwaway MySQL container is started with Testcontainers (Docker must be running). Set
TEST_DATABASE_URL to use an existing database instead. Without either, these tests are skipped
with a clear reason rather than failing.
"""

from __future__ import annotations

import os
from decimal import Decimal
from uuid import uuid4

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import sessionmaker

from app.config import Settings
from app.db.repository import SqlDocumentRepository
from app.extraction import extract_document
from app.review import review
from app.security import tin_hash
from app.services.documents import apply_correction, from_extraction
from tests.fixtures import load_fixture

SETTINGS = Settings(tin_hmac_key="test-hmac-key")
ALEMBIC_INI = os.path.join(os.path.dirname(__file__), "..", "alembic.ini")


@pytest.fixture(scope="module")
def db_url():
    if url := os.environ.get("TEST_DATABASE_URL"):
        yield url
        return
    try:
        from testcontainers.community.mysql import MySqlContainer

        container = MySqlContainer("mysql:8.4", dialect="pymysql")
        container.start()
    except Exception as exc:  # Docker not installed / not running
        pytest.skip(f"MySQL tests need Docker (or TEST_DATABASE_URL): {exc}")
    try:
        yield container.get_connection_url()
    finally:
        container.stop()


def alembic_config(url: str) -> Config:
    cfg = Config(ALEMBIC_INI)
    cfg.set_main_option("script_location", os.path.join(os.path.dirname(ALEMBIC_INI), "migrations"))
    cfg.set_main_option("sqlalchemy.url", url)
    return cfg


@pytest.fixture(scope="module")
def engine(db_url):
    command.upgrade(alembic_config(db_url), "head")
    eng = create_engine(db_url)
    yield eng
    eng.dispose()


@pytest.fixture
def repo(engine):
    session = sessionmaker(bind=engine, expire_on_commit=False)()
    yield SqlDocumentRepository(session)
    session.close()


def stored(name: str):
    result = review(extract_document(load_fixture(name)), settings=SETTINGS)
    return from_extraction(uuid4(), f"documents/{uuid4()}.pdf", 2026, result, SETTINGS)


def test_migration_creates_expected_schema(engine):
    insp = inspect(engine)
    assert {"documents", "extracted_fields"} <= set(insp.get_table_names())
    columns = {c["name"]: c for c in insp.get_columns("extracted_fields")}
    assert (
        str(columns["value_decimal"]["type"])
        .upper()
        .startswith(("DECIMAL(12, 2)", "NUMERIC(12, 2)"))
    )
    assert "ix_documents_taxpayer_tin_hash" in {i["name"] for i in insp.get_indexes("documents")}


def test_round_trip_preserves_every_value(repo):
    original = stored("client-06_w2")  # two box 12 entries, no state row
    repo.add(original)
    loaded = repo.get(original.id)
    assert loaded.values == original.values
    assert loaded.fields == original.fields
    assert loaded.review_reasons == original.review_reasons
    assert loaded.warnings == original.warnings
    # MySQL DATETIME drops microseconds; compare to the second.
    assert abs((loaded.created_at - original.created_at).total_seconds()) < 1


def test_money_is_stored_as_exact_decimal(repo, engine):
    doc = stored("client-01_w2")
    repo.add(doc)
    with engine.connect() as conn:
        value = conn.execute(
            text(
                "SELECT value_decimal FROM extracted_fields "
                "WHERE document_id = :id AND field_name = 'box6_medicare_tax'"
            ),
            {"id": str(doc.id)},
        ).scalar_one()
    assert value == Decimal("3182.00")


def test_full_ssn_never_reaches_the_database(repo, engine):
    doc = stored("client-01_w2")
    repo.add(doc)
    with engine.connect() as conn:
        rows = conn.execute(
            text("SELECT value_text, tin_hash FROM extracted_fields WHERE document_id = :id"),
            {"id": str(doc.id)},
        ).all()
    dump = str(rows)
    assert "000-14-2201" not in dump and "000142201" not in dump
    assert "***-**-2201" in dump


def test_documents_for_the_same_taxpayer_can_be_matched(repo):
    w2_a, w2_b = stored("client-10_w2_1"), stored("client-10_w2_2")  # same employee, 2 jobs
    other = stored("client-01_w2")
    for doc in (w2_a, w2_b, other):
        repo.add(doc)
    matches = repo.list_by_taxpayer_hash(tin_hash("000-94-2780", "test-hmac-key"))
    assert {d.id for d in matches} == {w2_a.id, w2_b.id}


def test_correction_is_persisted_with_audit_fields(repo):
    doc = stored("client-01_w2")
    repo.add(doc)
    updated = apply_correction(doc, "box17_state_tax", "8900.00", "paul", SETTINGS)
    repo.update(updated)
    loaded = repo.get(doc.id)
    assert loaded.values["box17_state_tax"] == Decimal("8900.00")
    assert loaded.fields["box17_state_tax"].corrected_by == "paul"
    assert loaded.fields["box17_state_tax"].corrected_at is not None


def test_field_review_reasons_are_persisted(repo):
    doc = stored("client-01_w2")
    repo.add(doc)
    updated = apply_correction(doc, "box4_ss_tax", "1439.00", "paul", SETTINGS)
    repo.update(updated)

    loaded = repo.get(doc.id)
    [reason] = loaded.fields["box4_ss_tax"].review_reasons
    assert "6.2% of box 3" in reason
    assert loaded.fields["box1_wages"].review_reasons == []


def test_rows_from_before_migration_0003_read_as_no_reasons(repo, engine):
    """Existing databases have NULL in the new column; they must still load."""
    doc = stored("client-04_w2")
    repo.add(doc)
    with engine.begin() as conn:
        conn.execute(
            text("UPDATE extracted_fields SET review_reasons = NULL WHERE document_id = :id"),
            {"id": str(doc.id)},
        )
    loaded = repo.get(doc.id)
    assert all(meta.review_reasons == [] for meta in loaded.fields.values())


def test_client_ref_column_and_listing(repo, engine):
    insp = inspect(engine)
    assert "client_ref" in {c["name"] for c in insp.get_columns("documents")}
    assert "ix_documents_client_ref" in {i["name"] for i in insp.get_indexes("documents")}

    older = stored("client-10_w2_1").model_copy(update={"client_ref": "client-10"})
    newer = stored("client-10_w2_2").model_copy(update={"client_ref": "client-10"})
    newer = newer.model_copy(update={"created_at": older.created_at.replace(year=2027)})
    repo.add(older)
    repo.add(newer)
    assert [d.id for d in repo.list_by_client("client-10")] == [newer.id, older.id]
    assert repo.list_by_client("nobody") == []


def test_migration_downgrade_and_upgrade(db_url, engine):
    cfg = alembic_config(db_url)
    command.downgrade(cfg, "base")
    assert "documents" not in inspect(create_engine(db_url)).get_table_names()
    command.upgrade(cfg, "head")
