"""Persistence for StoredDocument.

`DocumentRepository` is the interface the API depends on. `SqlDocumentRepository` is the real
MySQL implementation; `InMemoryDocumentRepository` lets API tests run without a database.
The SQL repository is tested separately against a real MySQL (tests/test_db.py).
"""

from __future__ import annotations

import copy
import json
from datetime import UTC, datetime
from decimal import Decimal
from typing import Protocol
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.tables import DocumentRow, FieldRow
from app.models import FieldMeta, FormType
from app.services.documents import StoredDocument, jsonable


class DocumentRepository(Protocol):
    def add(self, doc: StoredDocument) -> None: ...
    def get(self, document_id: UUID) -> StoredDocument | None: ...
    def update(self, doc: StoredDocument) -> None: ...
    def list_by_taxpayer_hash(self, tin_hash: str) -> list[StoredDocument]: ...
    def list_by_client(self, client_ref: str) -> list[StoredDocument]: ...


class InMemoryDocumentRepository:
    def __init__(self) -> None:
        self._docs: dict[UUID, StoredDocument] = {}

    def add(self, doc: StoredDocument) -> None:
        self._docs[doc.id] = copy.deepcopy(doc)

    def get(self, document_id: UUID) -> StoredDocument | None:
        doc = self._docs.get(document_id)
        return copy.deepcopy(doc) if doc else None

    def update(self, doc: StoredDocument) -> None:
        self.add(doc)

    def list_by_taxpayer_hash(self, tin_hash: str) -> list[StoredDocument]:
        return [copy.deepcopy(d) for d in self._docs.values() if d.taxpayer_tin_hash == tin_hash]

    def list_by_client(self, client_ref: str) -> list[StoredDocument]:
        docs = [d for d in self._docs.values() if d.client_ref == client_ref]
        return [copy.deepcopy(d) for d in sorted(docs, key=lambda d: d.created_at, reverse=True)]


# --- MySQL ---------------------------------------------------------------------------------


def _naive_utc(dt: datetime | None) -> datetime | None:
    """MySQL DATETIME has no time zone; we always store UTC."""
    return dt.astimezone(UTC).replace(tzinfo=None) if dt else None


def _aware_utc(dt: datetime | None) -> datetime | None:
    return dt.replace(tzinfo=UTC) if dt else None


def _fill_field_row(row: FieldRow, name: str, doc: StoredDocument) -> None:
    value = doc.values.get(name)
    meta = doc.fields.get(name)
    row.field_name = name
    row.value_decimal = value if isinstance(value, Decimal) else None
    row.value_text = None if isinstance(value, Decimal) else json.dumps(jsonable(value))
    row.tin_hash = doc.tin_hashes.get(name)
    row.confidence = meta.confidence if meta else None
    row.source_label = meta.source_label if meta else None
    row.needs_review = meta.needs_review if meta else False
    row.corrected_by = meta.corrected_by if meta else None
    row.corrected_at = _naive_utc(meta.corrected_at) if meta else None


def _fill_document_row(row: DocumentRow, doc: StoredDocument) -> None:
    row.id = str(doc.id)
    row.form_type = doc.form_type.value
    row.tax_year = doc.tax_year
    row.s3_key = doc.s3_key
    row.client_ref = doc.client_ref
    row.status = "needs_review" if doc.needs_review else "ready"
    row.needs_review = doc.needs_review
    row.review_reasons = doc.review_reasons
    row.warnings = doc.warnings
    row.taxpayer_tin_last4 = doc.taxpayer_tin_last4
    row.taxpayer_tin_hash = doc.taxpayer_tin_hash
    row.created_at = _naive_utc(doc.created_at)
    row.updated_at = _naive_utc(doc.updated_at)

    existing = {f.field_name: f for f in row.fields}
    for name in doc.values:
        field_row = existing.get(name) or FieldRow()
        _fill_field_row(field_row, name, doc)
        if name not in existing:
            row.fields.append(field_row)


def _to_stored(row: DocumentRow) -> StoredDocument:
    values, fields, hashes = {}, {}, {}
    for f in row.fields:
        values[f.field_name] = (
            f.value_decimal if f.value_decimal is not None else json.loads(f.value_text or "null")
        )
        if f.tin_hash:
            hashes[f.field_name] = f.tin_hash
        if f.confidence is not None:
            fields[f.field_name] = FieldMeta(
                confidence=f.confidence,
                source_label=f.source_label,
                needs_review=f.needs_review,
                corrected_by=f.corrected_by,
                corrected_at=_aware_utc(f.corrected_at),
            )
    # Box 12 amounts come back from JSON as strings; restore Decimals for consistency.
    if isinstance(values.get("box12"), list):
        values["box12"] = [{**e, "amount": Decimal(e["amount"])} for e in values["box12"]]
    return StoredDocument(
        id=UUID(row.id),
        form_type=FormType(row.form_type),
        tax_year=row.tax_year,
        s3_key=row.s3_key,
        client_ref=row.client_ref,
        values=values,
        fields=fields,
        tin_hashes=hashes,
        review_reasons=row.review_reasons,
        warnings=row.warnings,
        needs_review=row.needs_review,
        created_at=_aware_utc(row.created_at),
        updated_at=_aware_utc(row.updated_at),
    )


class SqlDocumentRepository:
    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, doc: StoredDocument) -> None:
        row = DocumentRow()
        _fill_document_row(row, doc)
        self.session.add(row)
        self.session.commit()

    def get(self, document_id: UUID) -> StoredDocument | None:
        row = self.session.get(DocumentRow, str(document_id))
        return _to_stored(row) if row else None

    def update(self, doc: StoredDocument) -> None:
        row = self.session.get(DocumentRow, str(doc.id))
        if row is None:
            raise LookupError(f"document {doc.id} not found")
        _fill_document_row(row, doc)
        self.session.commit()

    def list_by_taxpayer_hash(self, tin_hash: str) -> list[StoredDocument]:
        rows = self.session.scalars(
            select(DocumentRow)
            .where(DocumentRow.taxpayer_tin_hash == tin_hash)
            .order_by(DocumentRow.created_at)
        )
        return [_to_stored(r) for r in rows]

    def list_by_client(self, client_ref: str) -> list[StoredDocument]:
        rows = self.session.scalars(
            select(DocumentRow)
            .where(DocumentRow.client_ref == client_ref)
            .order_by(DocumentRow.created_at.desc(), DocumentRow.id)
        )
        return [_to_stored(r) for r in rows]
