## Why
The heart of the product: turn a client's tax picture into concrete opportunities with an
estimated dollar impact.

## What
- `sealed interface Strategy` with `evaluate(TaxProfile, TaxYearRules) -> StrategyResult`
  (applies / not applicable, estimated annual savings, assumptions, explanation)
- First strategy: **S-corp election** for sole proprietors. Estimate self-employment tax saved
  by paying a reasonable salary and taking the rest as distributions, minus estimated extra
  costs (payroll, separate return)
- Every assumption (e.g. reasonable-salary ratio, annual compliance cost) is configurable and
  listed in the result

## Acceptance criteria
- [ ] Recommends for client-02 and client-09; explains why it's marginal for client-07; not
      applicable for W-2-only clients (01, 04, 08)
- [ ] Results always carry an "illustrative estimate, not tax advice" disclaimer
- [ ] Parameterised unit tests with hand-checked expected values in the test names/comments
- [ ] ADR-0007: strategy engine design and how estimates are communicated
