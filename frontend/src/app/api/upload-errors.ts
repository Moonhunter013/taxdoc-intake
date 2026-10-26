import { HttpErrorResponse } from '@angular/common/http';

/**
 * Turns an extraction-service error into a message an advisor can act on. The service answers in
 * three shapes:
 *  - FastAPI's usual body: {"detail": "File is not a PDF"}
 *  - an incomplete form (422): {"detail", "form_type", "missing_fields": [...], ...}
 *  - FastAPI request validation (422): {"detail": [{"loc": [...], "msg": "..."}]}
 */
export function describeUploadError(error: HttpErrorResponse): string {
  const body = (error.error ?? {}) as Record<string, unknown>;
  const detail = body['detail'];

  switch (error.status) {
    case 0:
      return "Can't reach the document service. Check your connection and that the services are running.";
    case 413:
      return 'This file is over 5 MB. Upload a single-page PDF under 5 MB.';
    case 415:
      return "This isn't a PDF. Upload the form as a PDF file.";
    case 422:
      return describe422(body, detail);
    case 502:
      return 'The document reading service is unavailable right now. Please try again in a minute.';
    case 500:
      return 'Something went wrong on the server. Please try again later.';
    default:
      return `Upload failed (HTTP ${error.status}).`;
  }
}

function describe422(body: Record<string, unknown>, detail: unknown): string {
  const missing = body['missing_fields'];
  if (Array.isArray(missing) && missing.length > 0) {
    const form = typeof body['form_type'] === 'string' ? `this ${body['form_type']}` : 'this form';
    return `Some required fields on ${form} couldn't be read: ${missing.map(fieldLabel).join(', ')}. Try a clearer scan.`;
  }
  if (Array.isArray(detail)) {
    const messages = detail
      .map((d) =>
        d && typeof d === 'object' && 'msg' in d ? String((d as { msg: unknown }).msg) : '',
      )
      .filter(Boolean);
    return `The upload was rejected: ${messages.join('; ') || 'invalid request'}.`;
  }
  if (detail === 'PDF could not be read') {
    return "This PDF couldn't be opened. It may be damaged or password-protected.";
  }
  if (typeof detail === 'string' && detail.length > 0) {
    return detail.endsWith('.') ? detail : `${detail}.`;
  }
  return "This document couldn't be processed.";
}

/** "box1a_nonemployee_comp" -> "box 1a (nonemployee comp)"; "payer_name" -> "payer name". */
export function fieldLabel(field: string): string {
  const box = /^box(\d+[a-z]?)_(.+)$/.exec(field);
  if (box) {
    return `box ${box[1]} (${box[2].replaceAll('_', ' ')})`;
  }
  return field.replaceAll('_', ' ');
}
