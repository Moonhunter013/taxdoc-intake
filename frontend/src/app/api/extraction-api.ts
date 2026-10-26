import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, filter, map } from 'rxjs';

/** Form types extraction-service understands (its FormType enum). */
export type FormType = 'W-2' | '1099-NEC';

/**
 * A stored document as extraction-service returns it (DocumentResponse). Money values inside
 * `document` are decimal strings ("118500.00"); SSNs/EINs are always masked (ADR-0005).
 */
export interface ExtractedDocument {
  document_id: string;
  form_type: FormType;
  tax_year: number;
  client_ref: string | null;
  needs_review: boolean;
  document: Record<string, unknown>;
  /** How each value was read and whether it needs checking. Not every field has an entry. */
  fields: Record<string, FieldMeta>;
  /** Every reason in one list; each field's own reasons are also in fields[name]. */
  review_reasons: string[];
  warnings: string[];
}

/** extraction's FieldMeta for one value. */
export interface FieldMeta {
  /** Textract confidence, 0-100 (100 once corrected by a person). */
  confidence: number;
  source_label: string | null;
  needs_review: boolean;
  /** Why this field needs review (#26); empty when it doesn't. */
  review_reasons: string[];
  corrected_by: string | null;
  corrected_at: string | null;
}

/** A correction's value as extraction expects it: decimal string, text, boolean or box 12 rows. */
export type CorrectionValue = string | boolean | null | { code: string; amount: string }[];

/**
 * All of a client's documents (ClientDocumentsResponse). `warnings` is about the client as a
 * whole, e.g. documents belonging to more than one taxpayer, so possibly filed under the wrong
 * client (ADR-0006).
 */
export interface ClientDocuments {
  client_ref: string;
  documents: ExtractedDocument[];
  taxpayer_groups: string[][];
  warnings: string[];
}

/** What happens to one upload, in order: bytes going up, then Textract working, then the result. */
export type UploadEvent =
  | { kind: 'progress'; percent: number }
  | { kind: 'processing' }
  | { kind: 'done'; document: ExtractedDocument };

/** Talks to extraction-service through the dev proxy (/api/v1/documents -> :8000). */
@Injectable({ providedIn: 'root' })
export class ExtractionApi {
  private readonly http = inject(HttpClient);

  /**
   * Uploads one PDF for a client. Emits progress while the file is sent, `processing` once every
   * byte is up (Textract takes several seconds), then `done`. HTTP errors arrive as
   * HttpErrorResponse; turn them into messages with describeUploadError().
   */
  upload(file: File, clientRef: string): Observable<UploadEvent> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('client_ref', clientRef);
    return this.http
      .post<ExtractedDocument>('/api/v1/documents', form, {
        reportProgress: true,
        observe: 'events',
      })
      .pipe(
        map((event) => toUploadEvent(event)),
        filter((event): event is UploadEvent => event !== null),
      );
  }

  /** One stored document. A 404 means the ID is unknown. */
  getDocument(documentId: string): Observable<ExtractedDocument> {
    return this.http.get<ExtractedDocument>(`/api/v1/documents/${encodeURIComponent(documentId)}`);
  }

  /**
   * Corrects one field. extraction re-validates the whole document and re-runs the review rules,
   * so the returned document can gain or lose flags on other fields too. A 422 means the value
   * was rejected; its `detail` names the rule but never echoes the value (ADR-0005).
   */
  correctField(
    documentId: string,
    field: string,
    value: CorrectionValue,
    correctedBy: string,
  ): Observable<ExtractedDocument> {
    return this.http.patch<ExtractedDocument>(
      `/api/v1/documents/${encodeURIComponent(documentId)}/fields/${encodeURIComponent(field)}`,
      { value, corrected_by: correctedBy },
    );
  }

  /** Every document uploaded for a client, plus client-level warnings. Unknown client: empty list. */
  clientDocuments(clientRef: string): Observable<ClientDocuments> {
    return this.http.get<ClientDocuments>(
      `/api/v1/clients/${encodeURIComponent(clientRef)}/documents`,
    );
  }
}

function toUploadEvent(event: HttpEvent<ExtractedDocument>): UploadEvent | null {
  switch (event.type) {
    case HttpEventType.UploadProgress: {
      if (!event.total) {
        return null; // size unknown: nothing useful to show yet
      }
      return event.loaded >= event.total
        ? { kind: 'processing' }
        : { kind: 'progress', percent: Math.floor((100 * event.loaded) / event.total) };
    }
    case HttpEventType.Response:
      return event.body ? { kind: 'done', document: event.body } : null;
    default:
      return null;
  }
}
