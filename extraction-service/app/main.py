from fastapi import FastAPI

app = FastAPI(
    title="TaxDoc Extraction Service",
    version="0.1.0",
    description="Week 0 skeleton. Upload + Textract extraction arrive in Week 1.",
)


@app.get("/health", tags=["ops"])
def health() -> dict[str, str]:
    """Liveness probe used by docker compose and, later, the ECS/ALB health check."""
    return {"status": "ok", "service": "extraction-service"}
