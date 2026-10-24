# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-23

## Context
This is a portfolio project meant to show engineering judgment, not just working code.
Reviewers need to see *why* things were built the way they were.

## Decision
Record significant decisions as short ADRs in `docs/adr/`, using `0000-template.md`.
Write one whenever a choice is hard to reverse, surprising, or involved a real trade-off
(e.g. service boundaries, OCR confidence thresholds, LLM guardrails, migration approach).

## Consequences
Small writing overhead per decision; in exchange the repo explains itself and gives
concrete talking points for interviews.
