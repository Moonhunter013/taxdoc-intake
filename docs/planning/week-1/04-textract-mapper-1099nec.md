## Why
Half the sample clients are self-employed; their income only shows up on 1099-NEC.

## What
1099-NEC mapper reusing the generic parser from the W-2 parser issue. Note the **Rev. December 2026** layout splits
box 1 into 1a-1d (1a = nonemployee compensation).

## Acceptance criteria
- [ ] Payer/recipient names, TINs and addresses extracted
- [ ] Box 1a, box 4 and state boxes 5-7 extracted with confidence
- [ ] Clients with two 1099-NECs (02, 05) produce two separate documents
- [ ] Tests against fixtures
