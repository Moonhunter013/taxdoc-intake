# ADR-0003: Service boundaries (Python extraction, Java planning)

- **Status:** Accepted
- **Date:** 2026-10-24

## Context
The system has two very different jobs:

1. **Extraction:** take a PDF, call AWS Textract, turn OCR output into validated W-2/1099-NEC
   data, flag what a human should check. Heavy on AWS SDK calls, document parsing, and (from
   Week 3) LLM APIs.
2. **Planning:** apply tax rules to a client's documents and estimate the dollar impact of
   strategies (S-corp election, retirement contributions, HSA...). Heavy on business rules,
   precise money arithmetic, and long-lived domain logic that will grow every tax year.

The target team runs both Java (Spring Boot, plus legacy Jersey) and Python (FastAPI) services
with ChatGPT/Claude APIs, so the demo deliberately exercises both stacks.

## Options considered
1. **One service (all Python or all Java):** simplest to deploy and debug; one build, one
   database owner. Loses the chance to use each ecosystem where it is strongest, and doesn't
   reflect the target stack.
2. **Two services split by capability:** extraction (Python/FastAPI) and planning
   (Java/Spring Boot), communicating over HTTP. *(chosen)*
3. **More, finer-grained services** (separate OCR, review, persistence...): more network hops
   and deployment overhead with no benefit at this size.

## Decision
- **extraction-service (Python, FastAPI)** owns document intake: upload validation, S3,
  Textract, parsing, review rules, and the `documents` / `extracted_fields` tables.
  Python suits it: boto3 and the LLM SDKs are first-class, and parsing code iterates quickly
  against fixtures.
- **planning-service (Java 21, Spring Boot)** owns planning rules and their results. Java suits
  long-lived domain logic: strong typing, `BigDecimal`, records and sealed interfaces for
  strategy types, and it matches the team's main backend.
- **The boundary is the extraction API, not the database.** Planning reads documents through
  extraction's HTTP API (JSON, money as decimal strings, TINs masked). Each service owns its
  own tables; neither reads the other's. Sharing a database would couple the schemas and make
  either service impossible to change alone.
- Only reviewed data crosses the boundary: planning treats documents with `needs_review: true`
  as provisional and labels any estimates based on them.

## Consequences
- **Week 2 follow-ups:** add a "documents for a client" endpoint to extraction (matching on the
  taxpayer hash, ADR-0005); give planning its own MySQL schema and database user rather than
  sharing `taxdoc`.
- Two languages means two toolchains, two CI jobs and two container images. That cost is
  visible already (uv vs Maven, pytest vs JUnit) and is acceptable for a demo of this stack.
- An HTTP hop adds latency and a failure mode; planning must handle extraction being down
  (timeouts, clear errors) rather than assume it.
- **What would make us merge them:** if planning ever needed extraction's internals rather than
  its API, or if one person maintained both and the operational overhead outweighed the
  benefits. The API boundary keeps that option open: either side could be rewritten or merged
  without changing the other's callers.
