# ADR-0007: Strategy engine and how estimates are communicated

- **Status:** Accepted
- **Date:** 2026-10-25

## Context
Planning turns a client's `TaxProfile` (#21) into tax-saving opportunities with an estimated
dollar impact. Three things make this harder than it sounds:

1. **Some inputs are tax law, others are judgement.** The Social Security wage base is a published
   number (`tax-years.yml`, each value with its source link). An S-corp owner's "reasonable salary"
   is not: the IRS requires reasonable compensation for the owner's services but gives no formula
   ([IRS](https://www.irs.gov/businesses/small-businesses-self-employed/s-corporation-compensation-and-medical-insurance-issues)).
2. **The forms don't carry everything.** No filing status, age, business expenses or health plan.
3. **A confident wrong number is worse than none.** The output is shown to advisors (and, in Week 3,
   to an LLM and agents), so every estimate must say what it assumed.

## Options considered
1. **Rules engine (Drools, a DSL, or rules in a database):** rules editable without a deploy. But
   tax strategies are arithmetic with a few branches, not pattern matching over many facts; a
   rules engine adds a second language and makes the money maths harder to test and review.
2. **Open plugin interface (any Spring bean implementing `Strategy`):** easy to add strategies, but
   nothing stops an unreviewed one appearing, and the list of strategies is invisible.
3. **Sealed Java interface, one class per strategy, judgement calls in config.** *(chosen)*

## Decision
- `sealed interface Strategy permits ...` with `evaluate(TaxProfile, TaxYearRules) -> StrategyResult`.
  Adding a strategy means adding it to `permits`, so the set is explicit and reviewed. Strategies
  are pure functions of profile + rules + assumptions, which makes them trivial to unit test.
- **Three outcomes, not a yes/no:** `RECOMMENDED`, `MARGINAL` (saves something, explanation says why
  it's not recommended) and `NOT_APPLICABLE` (doesn't fit, or costs more than it saves). The
  borderline case (client-07) is the one an advisor most needs explained.
- **Law vs judgement are kept apart.** Limits and rates come only from `TaxYearRules`; rates that
  are derived (SE tax = both payroll halves) are computed, not configured. Judgement calls live in
  `strategy-assumptions.yml`, never in code.
- **Every result carries:** an estimated annual saving (cents, `BigDecimal`), a plain-English
  explanation with the key figures, the list of assumptions with the values used, and a fixed
  disclaimer ("Illustrative estimate ... not tax advice"). The `StrategyResult` constructor rejects
  a result without the disclaimer, and a "not applicable" result that claims savings.
- **Explanations show whole dollars** ("about $6,657"); the stored figure keeps cents.
- `StrategyEngine` runs all strategies and orders them: recommended, marginal, not applicable,
  larger savings first.

### First strategy: S-corp election
Compares Social Security + Medicare tax as a sole proprietor (Schedule SE plus Additional Medicare)
with payroll tax on an S-corp salary, minus extra running costs. Assumptions, chosen 2026-10-25:
salary = 60% of profit capped at $120,000; $2,500 a year extra costs; recommend from $5,000 net.
The cap matters: with a flat 60%, client-09's salary ($186,000) exceeds the wage base and the
estimate collapses to ~$1,200, which misrepresents a high earner whose salary would track the
market rate for the role, not profit.

| Client | Result | Net estimate |
|---|---|---|
| 02 ($185k 1099) | Recommended | $6,657 |
| 09 ($310k 1099) | Recommended | $11,097 |
| 05 ($130k 1099) | Marginal | $3,934 |
| 07 ($95k 1099) | Marginal | $2,202 |
| 03 (W-2 + $42k side gig) | Not applicable: saving below costs | - |
| 06 (W-2 at wage base + $35k) | Not applicable: S-corp costs more | - |
| 01, 04, 08, 10 (W-2 only) | Not applicable | - |

## Consequences
- **Known limitation: income tax is not modelled**, notably the QBI deduction, which an S-corp
  salary reduces and which can shrink the saving for high earners (client-09). Every S-corp result
  lists this. Modelling it needs QBI thresholds and SSTB rules in `tax-years.yml` (a later issue).
- Results assume filing status single and no business expenses, because the forms don't say.
  Collecting these per client would remove two assumptions from every result.
- Storing the outcome needs a column (`strategy_results` has only `applicable`); added with the
  Plans API (#24), which owns persistence of results.
- A rules engine stays possible later: the `Strategy` interface is the seam. We'd revisit if
  non-developers needed to change rules without a release.
