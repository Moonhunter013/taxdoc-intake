"""SQLAlchemy table definitions. The schema itself is created by Alembic migrations
(migrations/versions/), never by `create_all`, so production and tests use the same DDL."""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal

from sqlalchemy import (
    CHAR,
    JSON,
    BigInteger,
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    Numeric,
    SmallInteger,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class DocumentRow(Base):
    __tablename__ = "documents"

    id: Mapped[str] = mapped_column(CHAR(36), primary_key=True)
    form_type: Mapped[str] = mapped_column(String(16))
    tax_year: Mapped[int] = mapped_column(SmallInteger)
    s3_key: Mapped[str] = mapped_column(String(255), unique=True)
    status: Mapped[str] = mapped_column(String(16))  # "needs_review" | "ready"
    needs_review: Mapped[bool] = mapped_column(Boolean)
    review_reasons: Mapped[list] = mapped_column(JSON)
    warnings: Mapped[list] = mapped_column(JSON)
    # Taxpayer identity without the number itself (ADR-0005)
    taxpayer_tin_last4: Mapped[str | None] = mapped_column(CHAR(4))
    taxpayer_tin_hash: Mapped[str | None] = mapped_column(CHAR(64), index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime)  # UTC
    updated_at: Mapped[datetime] = mapped_column(DateTime)  # UTC

    fields: Mapped[list[FieldRow]] = relationship(
        back_populates="document", cascade="all, delete-orphan", lazy="selectin"
    )


class FieldRow(Base):
    __tablename__ = "extracted_fields"
    __table_args__ = (UniqueConstraint("document_id", "field_name"),)

    id: Mapped[int] = mapped_column(
        BigInteger().with_variant(Integer, "sqlite"), primary_key=True, autoincrement=True
    )
    document_id: Mapped[str] = mapped_column(ForeignKey("documents.id", ondelete="CASCADE"))
    field_name: Mapped[str] = mapped_column(String(64))
    # Money goes in an exact DECIMAL column; everything else is JSON-encoded text.
    value_decimal: Mapped[Decimal | None] = mapped_column(Numeric(12, 2))
    value_text: Mapped[str | None] = mapped_column(Text)
    tin_hash: Mapped[str | None] = mapped_column(CHAR(64))
    confidence: Mapped[float | None] = mapped_column(Float)
    source_label: Mapped[str | None] = mapped_column(String(255))
    needs_review: Mapped[bool] = mapped_column(Boolean, default=False)
    corrected_by: Mapped[str | None] = mapped_column(String(255))
    corrected_at: Mapped[datetime | None] = mapped_column(DateTime)

    document: Mapped[DocumentRow] = relationship(back_populates="fields")
