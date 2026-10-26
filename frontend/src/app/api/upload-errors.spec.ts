import { HttpErrorResponse } from '@angular/common/http';

import { describeUploadError, fieldLabel } from './upload-errors';

/** Bodies are the shapes extraction-service actually sends (app/api/documents.py). */
function error(status: number, body: unknown = null): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('describeUploadError', () => {
  it('explains a file over the size limit (413)', () => {
    expect(describeUploadError(error(413, { detail: 'File is larger than 5 MB' }))).toBe(
      'This file is over 5 MB. Upload a single-page PDF under 5 MB.',
    );
  });

  it('explains a file that is not a PDF (415)', () => {
    expect(describeUploadError(error(415, { detail: 'File is not a PDF' }))).toBe(
      "This isn't a PDF. Upload the form as a PDF file.",
    );
  });

  it('lists the fields Textract could not read on an incomplete form (422)', () => {
    const body = {
      detail: 'Required fields missing: box1a_nonemployee_comp, payer_name',
      form_type: '1099-NEC',
      missing_fields: ['box1a_nonemployee_comp', 'payer_name'],
      partial: {},
      warnings: [],
    };
    expect(describeUploadError(error(422, body))).toBe(
      "Some required fields on this 1099-NEC couldn't be read: box 1a (nonemployee comp), payer name. Try a clearer scan.",
    );
  });

  it('explains an unreadable PDF (422)', () => {
    expect(describeUploadError(error(422, { detail: 'PDF could not be read' }))).toBe(
      "This PDF couldn't be opened. It may be damaged or password-protected.",
    );
  });

  it("passes on the server's own sentence for other 422s, e.g. a multi-page PDF", () => {
    expect(
      describeUploadError(error(422, { detail: 'Expected a single-page form, got 3 pages' })),
    ).toBe('Expected a single-page form, got 3 pages.');
  });

  it("reads FastAPI's list-style validation detail (422)", () => {
    const body = {
      detail: [
        { loc: ['body', 'client_ref'], msg: "String should match pattern '^[A-Za-z0-9]...'" },
      ],
    };
    expect(describeUploadError(error(422, body))).toBe(
      "The upload was rejected: String should match pattern '^[A-Za-z0-9]...'.",
    );
  });

  it('explains that AWS is unavailable (502)', () => {
    expect(
      describeUploadError(error(502, { detail: 'Document storage or OCR service unavailable' })),
    ).toContain('unavailable right now');
  });

  it('explains a network failure (status 0)', () => {
    expect(describeUploadError(error(0))).toContain("Can't reach the document service");
  });

  it('falls back to the status code for anything unexpected', () => {
    expect(describeUploadError(error(418))).toBe('Upload failed (HTTP 418).');
  });
});

describe('fieldLabel', () => {
  it.each([
    ['box1a_nonemployee_comp', 'box 1a (nonemployee comp)'],
    ['box12', 'box12'],
    ['box4_ss_tax', 'box 4 (ss tax)'],
    ['employer_name', 'employer name'],
  ])('%s -> %s', (field, label) => {
    expect(fieldLabel(field)).toBe(label);
  });
});
