import { ExtractedDocument } from '../api/extraction-api';

/** A file whose content starts like a real PDF, padded to `size` bytes. */
export function pdf(name = 'form.pdf', size = 2048): File {
  const body = new Uint8Array(size);
  body.set(new TextEncoder().encode('%PDF-1.7\n'));
  return new File([body], name, { type: 'application/pdf' });
}

/** Shaped like extraction-service's DocumentResponse for client-03's W-2 (masked SSN). */
export const W2_DONE: ExtractedDocument = {
  document_id: '0b6f2a52-9c1e-4c7e-8d0a-1f2e3d4c5b6a',
  form_type: 'W-2',
  tax_year: 2026,
  client_ref: 'client-03',
  needs_review: false,
  document: {
    employer_name: 'Riverbend Health Systems',
    box1_wages: '52250.00',
    employee_ssn: '***-**-8810',
  },
  fields: {
    employer_name: {
      confidence: 97.1,
      source_label: 'Employer name',
      needs_review: false,
      review_reasons: [],
      corrected_by: null,
      corrected_at: null,
    },
    box1_wages: {
      confidence: 96.4,
      source_label: '1 Wages, tips',
      needs_review: false,
      review_reasons: [],
      corrected_by: null,
      corrected_at: null,
    },
  },
  review_reasons: [],
  warnings: [],
};

/** A 1099-NEC that extraction flagged for review. */
export const NEC_NEEDS_REVIEW: ExtractedDocument = {
  document_id: '7c1d9e40-2b3a-4f5e-9a8b-6c5d4e3f2a1b',
  form_type: '1099-NEC',
  tax_year: 2026,
  client_ref: 'client-03',
  needs_review: true,
  document: { payer_name: 'Pixelforge Studio LLC', box1a_nonemployee_comp: '42000.00' },
  fields: {
    payer_name: {
      confidence: 95.0,
      source_label: "PAYER'S name",
      needs_review: false,
      review_reasons: [],
      corrected_by: null,
      corrected_at: null,
    },
    box1a_nonemployee_comp: {
      confidence: 98.2,
      source_label: '1a Nonemployee compensation',
      needs_review: false,
      review_reasons: [],
      corrected_by: null,
      corrected_at: null,
    },
    box6_state: {
      confidence: 64.2,
      source_label: '6 State',
      needs_review: true,
      review_reasons: ['box6_state: confidence 64.2% is below the 70% threshold'],
      corrected_by: null,
      corrected_at: null,
    },
  },
  review_reasons: ['box6_state: confidence 64.2% is below the 70% threshold'],
  warnings: [],
};
