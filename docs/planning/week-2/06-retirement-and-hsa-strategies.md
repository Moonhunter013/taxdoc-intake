## Why
Retirement contributions and HSAs are the most common, lowest-risk savings opportunities.

## What
- **Solo 401(k) / SEP-IRA** for self-employment income: contribution room and estimated tax
  saved (side gig in client-03, full self-employment in 02, 05, 09)
- **Workplace 401(k) headroom**: W-2 deferral below the annual limit (client-01)
- **HSA**: no HSA contributions on the W-2 (client-01); flag eligibility as an assumption,
  since HDHP coverage isn't on the forms
- **Excess Social Security withholding**: two employers together withheld more than the annual
  maximum (client-10); the excess is a refundable credit

## Acceptance criteria
- [ ] Each strategy has positive, negative and edge-case tests based on the sample clients
- [ ] Limits come from `TaxYearRules`, never literals
- [ ] Control clients (04, 08) get few or no recommendations, and the tests assert that
