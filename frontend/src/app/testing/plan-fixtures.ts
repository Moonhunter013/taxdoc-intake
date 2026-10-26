import { Plan, PlanResult } from '../api/planning-api';

/**
 * Plans with the real figures planning-service produces for the sample clients (verified live in
 * #24 and by its tests): client-02 S-corp 6,656.67 + Solo 401(k) 6,660.00; client-07 Solo 401(k)
 * 3,135.00 with a marginal S-corp 2,202.07; control client-04 with only marginal results.
 */
function result(
  code: string,
  title: string,
  outcome: PlanResult['outcome'],
  savings: string,
  explanation: string,
): PlanResult {
  return {
    code,
    title,
    outcome,
    estimatedSavings: savings,
    explanation,
    assumptions:
      outcome === 'NOT_APPLICABLE'
        ? []
        : ['Filing status single; federal taxes only.', 'Illustrative assumption for the test.'],
  };
}

const DISCLAIMER =
  'Illustrative estimate based on synthetic data and simplifying assumptions; not tax advice.';

function plan(id: string, clientRef: string, total: string, results: PlanResult[]): Plan {
  return {
    id,
    clientRef,
    taxYear: 2026,
    provisional: false,
    provisionalNotice: null,
    documentsNeedingReview: [],
    totalEstimatedSavings: total,
    results,
    warnings: [],
    createdAt: '2026-10-26T18:00:00.123456Z',
    disclaimer: DISCLAIMER,
  };
}

const notApplicable = (code: string, title: string, why: string) =>
  result(code, title, 'NOT_APPLICABLE', '0.00', why);

export const CLIENT02_PLAN = plan('8a1f0c3e-0002-4b2a-9c1d-000000000002', 'client-02', '13316.67', [
  result(
    'SOLO_401K',
    'Open a Solo 401(k) for self-employment income',
    'RECOMMENDED',
    '6660.00',
    'A Solo 401(k) could take up to $58,886 of $185,000 in self-employment income.',
  ),
  result(
    'S_CORP_ELECTION',
    'Elect S-corporation status',
    'RECOMMENDED',
    '6656.67',
    'As a sole proprietor, $185,000 of self-employment income means about $26,140 of Social Security and Medicare tax.',
  ),
  notApplicable(
    'EXCESS_SOCIAL_SECURITY',
    'Claim excess Social Security tax withheld',
    'Fewer than two W-2s.',
  ),
  notApplicable(
    'HSA',
    'Contribute to a health savings account (HSA)',
    'No W-2 employer to contribute through payroll.',
  ),
  notApplicable(
    'WORKPLACE_401K',
    'Increase workplace 401(k) contributions',
    'No W-2 employment, so no workplace plan.',
  ),
]);

export const CLIENT07_PLAN = plan('8a1f0c3e-0007-4b2a-9c1d-000000000007', 'client-07', '3135.00', [
  result(
    'SOLO_401K',
    'Open a Solo 401(k) for self-employment income',
    'RECOMMENDED',
    '3135.00',
    'Contributing $14,250 would lower federal income tax by about $3,135.',
  ),
  result(
    'S_CORP_ELECTION',
    'Elect S-corporation status',
    'MARGINAL',
    '2202.07',
    'That leaves about $2,202 a year, below the $5,000 this engine requires to recommend it.',
  ),
  notApplicable(
    'EXCESS_SOCIAL_SECURITY',
    'Claim excess Social Security tax withheld',
    'Fewer than two W-2s.',
  ),
  notApplicable('HSA', 'Contribute to a health savings account (HSA)', 'No W-2 employer.'),
  notApplicable('WORKPLACE_401K', 'Increase workplace 401(k) contributions', 'No W-2 employment.'),
]);

export const CLIENT04_PLAN = plan('8a1f0c3e-0004-4b2a-9c1d-000000000004', 'client-04', '0.00', [
  result(
    'WORKPLACE_401K',
    'Increase workplace 401(k) contributions',
    'MARGINAL',
    '354.00',
    "That's below the $1,000 this engine requires to recommend it.",
  ),
  result(
    'HSA',
    'Contribute to a health savings account (HSA)',
    'MARGINAL',
    '275.10',
    "That's below the $1,000 this engine requires to recommend it.",
  ),
  notApplicable(
    'S_CORP_ELECTION',
    'Elect S-corporation status',
    'No self-employment (1099-NEC) income.',
  ),
  notApplicable(
    'SOLO_401K',
    'Open a Solo 401(k) for self-employment income',
    'No self-employment income.',
  ),
  notApplicable(
    'EXCESS_SOCIAL_SECURITY',
    'Claim excess Social Security tax withheld',
    'Fewer than two W-2s.',
  ),
]);

export const NEEDS_REVIEW_DOC = 'd4aa262a-604d-4e88-980a-7cc568650811';

export const CLIENT02_PROVISIONAL: Plan = {
  ...CLIENT02_PLAN,
  provisional: true,
  provisionalNotice:
    'Provisional: some source documents still need review. These estimates may change once the flagged fields are checked.',
  documentsNeedingReview: [NEEDS_REVIEW_DOC],
};
