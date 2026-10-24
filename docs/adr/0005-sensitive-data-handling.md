# ADR-0005: Sensitive data handling (SSNs and EINs)

- **Status:** Accepted
- **Date:** 2026-10-24

## Context
W-2s and 1099-NECs carry Social Security numbers and Employer Identification Numbers. A leaked
SSN enables identity theft and tax-refund fraud, so how we store, log and display them matters
more than almost any other design choice in this service.

What the application actually needs:
- **Display:** an advisor needs to recognise a document ("***-**-2201"), not read the number.
- **Matching:** documents for the same taxpayer (e.g. client-10's two W-2s) must be linkable.
- **Planning:** no planning strategy uses the SSN itself.

## Options considered
1. **Plaintext, relying on database encryption at rest:** protects against stolen disks only.
   Anyone with database access (a leaked credential, a backup, a SQL injection) reads every SSN.
2. **Encrypt the full value (AES-GCM, app-held key) plus a hash for lookups:** recoverable if a
   future feature needs the full number, but adds key rotation, a decryption path to secure,
   and the full number still exists in the database.
3. **Minimise: keep only the last 4 digits plus a keyed hash.** *(chosen)*

## Decision
- Full SSNs/EINs are never written to the database or logs, and never returned by the API.
- Each TIN field stores its **masked value** (`***-**-2201`) and an **HMAC-SHA256** of
  `"<ssn|ein>:<digits>"` keyed with `EXTRACTION_TIN_HMAC_KEY`. Including the kind prevents an
  SSN and an EIN with the same nine digits from colliding.
- A *keyed* hash is essential: there are only 10^9 SSNs, so a plain SHA-256 could be reversed by
  brute force in minutes. Without the key, the HMAC can't be reversed or recomputed.
- The taxpayer's hash and last 4 are also on the `documents` row (indexed) for matching.
- Models declare TIN fields with `repr=False`, so they never appear when objects are printed or
  logged. Validation errors on a corrected SSN report the rule that failed, never the value.
- The service refuses to store documents (HTTP 500, logged) if the key isn't configured, rather
  than storing unprotected data.

## Consequences
- **The full number is unrecoverable from our database by design.** A future feature that
  genuinely needs it (e.g. e-filing) would have to collect it again or revisit this ADR.
- **The key must be protected and kept stable:** in AWS it belongs in Secrets Manager, injected
  into the ECS task. Losing or rotating it breaks matching for existing rows (they would need
  re-hashing from source documents).
- **The uploaded PDF in S3 still contains the full number** until the bucket's 30-day lifecycle
  rule deletes it. The bucket blocks all public access and only the service's IAM identity can
  read it. A shorter expiry, or deleting the PDF after successful extraction, is a possible
  follow-up.
- Re-validating a stored document uses valid-format placeholder TINs, because the real values
  no longer exist. This only affects validation, never stored or displayed data.
