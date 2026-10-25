## Why
The payoff screen: what the advisor would show the client.

## What
- Route `/clients/:clientRef/plan`: generate a plan, list strategies by estimated savings with
  explanation and assumptions, total estimated savings, disclaimer
- Clear banner when the plan is provisional because documents still need review, with links to
  those documents

## Acceptance criteria
- [ ] Currency formatted from decimal strings (no floating-point rounding in the UI)
- [ ] Empty state for clients with no applicable strategies
- [ ] Component tests; one Playwright smoke test of upload -> review -> plan (optional stretch)
