from fastapi import FastAPI

from app.api.documents import router as documents_router

app = FastAPI(
    title="TaxDoc Extraction Service",
    version="0.2.0",
    description="Upload W-2 and 1099-NEC PDFs; get validated, structured data back.",
)
app.include_router(documents_router)


@app.get("/health", tags=["ops"])
def health() -> dict[str, str]:
    """Liveness probe used by docker compose and, later, the ECS/ALB health check."""
    return {"status": "ok", "service": "extraction-service"}
