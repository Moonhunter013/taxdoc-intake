"""Store each field's review reasons (#26), so the review screen can show them next to the box.

Revision ID: 0003
Revises: 0002
Create Date: 2026-10-26
"""

import sqlalchemy as sa
from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Nullable without a default: MySQL JSON columns can't take a literal default, and rows
    # written before this migration are read back as an empty list. The document-level
    # review_reasons are unchanged and still list every reason.
    op.add_column("extracted_fields", sa.Column("review_reasons", sa.JSON(), nullable=True))


def downgrade() -> None:
    op.drop_column("extracted_fields", "review_reasons")
