import { ExtractedDocument, FieldMeta } from '../api/extraction-api';

export function meta(
  confidence: number,
  reasons: string[] = [],
  extra: Partial<FieldMeta> = {},
): FieldMeta {
  return {
    confidence,
    source_label: 'from Textract',
    needs_review: reasons.length > 0,
    review_reasons: reasons,
    corrected_by: null,
    corrected_at: null,
    ...extra,
  };
}

/**
 * client-01's W-2 as extraction returns it (values from samples/ground-truth/client-01.json):
 * masked TINs, money as decimal strings, every box present.
 */
export function client01W2(changes: Partial<ExtractedDocument> = {}): ExtractedDocument {
  return {
    document_id: '5f0c2d1e-8a7b-4c3d-9e2f-1a0b9c8d7e6f',
    form_type: 'W-2',
    tax_year: 2026,
    client_ref: 'client-01',
    needs_review: false,
    document: {
      employer_name: 'Halcyon Robotics Inc',
      employer_ein: '**-***2201',
      employer_address: '1200 Pearl Pkwy, Boulder, CO 80301',
      employee_name: 'Avery Lindqvist',
      employee_ssn: '***-**-2201',
      box1_wages: '203000.00',
      box2_federal_withholding: '42630.00',
      box3_ss_wages: '184500.00',
      box4_ss_tax: '11439.00',
      box5_medicare_wages: '212000.00',
      box6_medicare_tax: '3182.00',
      box12: [{ code: 'D', amount: '9000.00' }],
      box13_retirement_plan: true,
      box15_state: 'CO',
      box15_state_id: '00012201',
      box16_state_wages: '203000.00',
      box17_state_tax: '8932.00',
    },
    fields: {
      employer_name: meta(97.9),
      employee_ssn: meta(99.1),
      box1_wages: meta(96.4),
      box3_ss_wages: meta(95.8),
      box4_ss_tax: meta(96.0),
      box12: meta(93.0),
      box13_retirement_plan: meta(91.0),
      box15_state: meta(73.2),
    },
    review_reasons: [],
    warnings: [],
    ...changes,
  };
}

/** What extraction returns after box 4 is set to 1,439.00: the 6.2% rule flags box 4 only. */
export function client01W2WithBadBox4(): ExtractedDocument {
  const reason = 'box 4 is $1,439.00 but 6.2% of box 3 is $11,439.00';
  const base = client01W2();
  return {
    ...base,
    needs_review: true,
    document: { ...base.document, box4_ss_tax: '1439.00' },
    fields: {
      ...base.fields,
      box4_ss_tax: meta(100, [reason], {
        corrected_by: 'Paul',
        corrected_at: '2026-10-26T17:40:00Z',
      }),
    },
    review_reasons: [reason],
  };
}
