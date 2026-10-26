import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { W2_DONE } from '../testing/fixtures';
import { ExtractionApi, UploadEvent } from './extraction-api';

describe('ExtractionApi', () => {
  let api: ExtractionApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ExtractionApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the file and client_ref as multipart form data', () => {
    const file = new File(['%PDF-1.7'], 'client-03_w2.pdf', { type: 'application/pdf' });
    api.upload(file, 'client-03').subscribe();

    const req = http.expectOne('/api/v1/documents');
    expect(req.request.method).toBe('POST');
    expect(req.request.reportProgress).toBe(true);
    const body = req.request.body as FormData;
    expect(body.get('client_ref')).toBe('client-03');
    expect((body.get('file') as File).name).toBe('client-03_w2.pdf');
    req.flush(W2_DONE, { status: 201, statusText: 'Created' });
  });

  it("reads a client's documents and warnings", () => {
    let warnings: string[] = [];
    api.clientDocuments('client-10').subscribe((c) => (warnings = c.warnings));

    const req = http.expectOne('/api/v1/clients/client-10/documents');
    expect(req.request.method).toBe('GET');
    req.flush({ client_ref: 'client-10', documents: [], taxpayer_groups: [], warnings: ['check'] });
    expect(warnings).toEqual(['check']);
  });

  it('reports progress, then processing once every byte is sent, then the document', () => {
    const events: UploadEvent[] = [];
    api.upload(new File(['%PDF-1.7'], 'a.pdf'), 'client-03').subscribe((e) => events.push(e));

    const req = http.expectOne('/api/v1/documents');
    req.event({ type: HttpEventType.UploadProgress, loaded: 0, total: undefined }); // size unknown: ignored
    req.event({ type: HttpEventType.UploadProgress, loaded: 250, total: 1000 });
    req.event({ type: HttpEventType.UploadProgress, loaded: 1000, total: 1000 });
    req.flush(W2_DONE, { status: 201, statusText: 'Created' });

    expect(events).toEqual([
      { kind: 'progress', percent: 25 },
      { kind: 'processing' },
      { kind: 'done', document: W2_DONE },
    ]);
  });
});
