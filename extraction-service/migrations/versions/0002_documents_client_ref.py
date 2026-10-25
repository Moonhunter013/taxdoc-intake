"""Add client_ref to documents (ADR-0006).

Revision ID: 0002
Revises: 0001
Create Date: 2026-10-25
"""

import sqlalchemy as sa
from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Nullable: documents uploaded before this migration have no client.
    op.add_column("documents", sa.Column("client_ref", sa.String(64), nullable=True))
    op.create_index("ix_documents_client_ref", "documents", ["client_ref"])


def downgrade() -> None:
    op.drop_index("ix_documents_client_ref", table_name="documents")
    op.drop_column("documents", "client_ref")
