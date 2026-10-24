"""Create documents and extracted_fields tables.

Revision ID: 0001
Revises:
Create Date: 2026-10-24
"""

import sqlalchemy as sa
from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "documents",
        sa.Column("id", sa.CHAR(36), primary_key=True),
        sa.Column("form_type", sa.String(16), nullable=False),
        sa.Column("tax_year", sa.SmallInteger(), nullable=False),
        sa.Column("s3_key", sa.String(255), nullable=False, unique=True),
        sa.Column("status", sa.String(16), nullable=False),
        sa.Column("needs_review", sa.Boolean(), nullable=False),
        sa.Column("review_reasons", sa.JSON(), nullable=False),
        sa.Column("warnings", sa.JSON(), nullable=False),
        sa.Column("taxpayer_tin_last4", sa.CHAR(4), nullable=True),
        sa.Column("taxpayer_tin_hash", sa.CHAR(64), nullable=True),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("updated_at", sa.DateTime(), nullable=False),
    )
    op.create_index("ix_documents_taxpayer_tin_hash", "documents", ["taxpayer_tin_hash"])

    op.create_table(
        "extracted_fields",
        sa.Column(
            "id",
            sa.BigInteger().with_variant(sa.Integer(), "sqlite"),
            primary_key=True,
            autoincrement=True,
        ),
        sa.Column(
            "document_id",
            sa.CHAR(36),
            sa.ForeignKey("documents.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("field_name", sa.String(64), nullable=False),
        sa.Column("value_decimal", sa.Numeric(12, 2), nullable=True),
        sa.Column("value_text", sa.Text(), nullable=True),
        sa.Column("tin_hash", sa.CHAR(64), nullable=True),
        sa.Column("confidence", sa.Float(), nullable=True),
        sa.Column("source_label", sa.String(255), nullable=True),
        sa.Column("needs_review", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("corrected_by", sa.String(255), nullable=True),
        sa.Column("corrected_at", sa.DateTime(), nullable=True),
        sa.UniqueConstraint("document_id", "field_name", name="uq_extracted_fields_doc_field"),
    )


def downgrade() -> None:
    op.drop_table("extracted_fields")
    op.drop_index("ix_documents_taxpayer_tin_hash", table_name="documents")
    op.drop_table("documents")
