"""GET /api/v1/clients/{client_ref}/documents: everything uploaded for one client.

This is the endpoint planning-service reads (ADR-0003). It also checks that all of a client's
documents belong to the same taxpayer, using the keyed TIN hash (ADR-0005/0006).
"""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Path

from app.api.dependencies import get_repository
from app.api.schemas import CLIENT_REF_PATTERN, ClientDocumentsResponse, DocumentResponse
from app.db.repository import DocumentRepository
from app.services.documents import group_by_taxpayer

router = APIRouter(prefix="/api/v1/clients", tags=["clients"])


@router.get("/{client_ref}/documents", response_model=ClientDocumentsResponse)
def list_client_documents(
    client_ref: Annotated[str, Path(pattern=CLIENT_REF_PATTERN)],
    repo: Annotated[DocumentRepository, Depends(get_repository)],
):
    """Newest first. A client with no documents yet returns an empty list (not 404)."""
    docs = repo.list_by_client(client_ref)
    groups = group_by_taxpayer(docs)
    warnings = []
    if len(groups) > 1:
        warnings.append(
            f"Documents for this client belong to {len(groups)} different taxpayers "
            "(matched by SSN). Check for documents filed under the wrong client."
        )
    return ClientDocumentsResponse(
        client_ref=client_ref,
        documents=[DocumentResponse.from_stored(d) for d in docs],
        taxpayer_groups=groups,
        warnings=warnings,
    )
