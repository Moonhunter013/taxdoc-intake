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
  review_reasons: string[];
  warnings: string[];
}

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
