## Why
Week 1 contains the project's most important design decisions. Write them down while they're fresh.

## What
- **ADR-0003: Service boundaries.** Why extraction is Python/FastAPI and planning is Java/Spring Boot,
  what crosses the boundary, and what would make you merge them.
- **ADR-0004: Extraction confidence & review.** Threshold choice, consistency rules, what happens to
  flagged fields.
- **ADR-0005: Sensitive data handling.** How SSNs/TINs are stored, logged and displayed.

## Acceptance criteria
- [ ] Each ADR uses `docs/adr/0000-template.md` and lists the options you rejected
- [ ] README links to them
