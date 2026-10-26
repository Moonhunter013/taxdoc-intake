import { HttpEventType, provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MAX_UPLOAD_BYTES } from './file-checks';
import { NEC_NEEDS_REVIEW, W2_DONE, pdf } from '../testing/fixtures';
import { UploadPage } from './upload-page';

/** Lets pending promises (file checks) and timers run, without waiting for HTTP to finish. */
const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

const CLIENT_DOCS = '/api/v1/clients/client-03/documents';

/** extraction's ClientDocumentsResponse with the given client-level warnings. */
function clientDocs(warnings: string[]) {
  return { client_ref: 'client-03', documents: [], taxpayer_groups: [], warnings };
}

const MISFILING =
  'Documents for this client belong to 2 different taxpayers (matched by SSN). ' +
  'Check for documents filed under the wrong client.';

describe('UploadPage', () => {
  let fixture: ComponentFixture<UploadPage>;
  let page: UploadPage;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [UploadPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(UploadPage);
    fixture.componentRef.setInput('clientRef', 'client-03');
    page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement as HTMLElement;
    await render();
  });

  afterEach(() => {
    // Tests that aren't about client warnings: answer the page's warning lookups with "none".
    http.match(CLIENT_DOCS).forEach((req) => req.flush(clientDocs([])));
    http.verify();
  });

  async function render(): Promise<void> {
    await settle();
    fixture.detectChanges();
  }

  /** The upload request for the file being sent now. */
  async function pendingUpload(): Promise<TestRequest> {
    await settle();
    return http.expectOne('/api/v1/documents');
  }

  function row(index = 0): HTMLElement {
    return el.querySelectorAll<HTMLElement>('li[data-status]')[index];
  }

  // --- success ---------------------------------------------------------------------------

  it('shows progress, then "Reading the form", then the result', async () => {
    page.addFiles([pdf('client-03_w2.pdf')]);
    const req = await pendingUpload();
    expect(req.request.body.get('client_ref')).toBe('client-03');

    req.event({ type: HttpEventType.UploadProgress, loaded: 40, total: 100 });
    await render();
    expect(row().textContent).toContain('Uploading 40%');

    req.event({ type: HttpEventType.UploadProgress, loaded: 100, total: 100 });
    await render();
    expect(row().textContent).toContain('Reading the form');

    req.flush(W2_DONE, { status: 201, statusText: 'Created' });
    await render();
    expect(row().dataset['status']).toBe('done');
    expect(row().textContent).toContain('Ready');
    expect(row().textContent).toContain('W-2 from Riverbend Health Systems, tax year 2026');
    expect(row().querySelector('a')).toBeNull(); // nothing to review
  });

  it('flags a document that needs review, with the reasons and a link to review it', async () => {
    page.addFiles([pdf('client-03_1099nec.pdf')]);
    (await pendingUpload()).flush(NEC_NEEDS_REVIEW, { status: 201, statusText: 'Created' });
    await render();

    expect(row().textContent).toContain('Needs review');
    expect(row().textContent).toContain('1099-NEC from Pixelforge Studio LLC');
    expect(row().textContent).toContain('box6_state: confidence 64.2% is below the 70% threshold');
    const link = row().querySelector('a')!;
    expect(link.textContent).toContain('Review fields');
    expect(link.getAttribute('href')).toBe(
      `/clients/client-03/documents/${NEC_NEEDS_REVIEW.document_id}`,
    );
  });

  // --- server errors ---------------------------------------------------------------------

  it.each([
    [413, { detail: 'File is larger than 5 MB' }, 'over 5 MB'],
    [415, { detail: 'File is not a PDF' }, "isn't a PDF"],
    [
      422,
      {
        detail: 'Required fields missing',
        form_type: 'W-2',
        missing_fields: ['box1_wages'],
        partial: {},
        warnings: [],
      },
      "Some required fields on this W-2 couldn't be read: box 1 (wages)",
    ],
    [502, { detail: 'Document storage or OCR service unavailable' }, 'unavailable right now'],
  ])('shows a friendly message for HTTP %i', async (status, body, message) => {
    page.addFiles([pdf()]);
    (await pendingUpload()).flush(body, { status, statusText: 'Error' });
    await render();

    expect(row().dataset['status']).toBe('failed');
    expect(row().textContent).toContain('Failed');
    expect(row().querySelector('[role="alert"]')!.textContent).toContain(message);
  });

  it('keeps going after a failure: the next file is still uploaded', async () => {
    page.addFiles([pdf('bad.pdf'), pdf('good.pdf')]);
    (await pendingUpload()).flush({ detail: 'x' }, { status: 502, statusText: 'Bad Gateway' });
    (await pendingUpload()).flush(W2_DONE, { status: 201, statusText: 'Created' });
    await render();

    expect(row(0).dataset['status']).toBe('failed');
    expect(row(1).dataset['status']).toBe('done');
  });

  // --- client-side checks ----------------------------------------------------------------

  it('rejects a file over 5 MB without sending it', async () => {
    page.addFiles([pdf('huge.pdf', MAX_UPLOAD_BYTES + 1)]);
    await render();

    http.expectNone('/api/v1/documents');
    expect(row().dataset['status']).toBe('rejected');
    expect(row().textContent).toContain('Not uploaded');
    expect(row().textContent).toContain('The limit is 5 MB');
  });

  it('rejects a non-PDF without sending it, even when it is named .pdf', async () => {
    page.addFiles([
      new File([new TextEncoder().encode('GIF89a')], 'w2.pdf', { type: 'application/pdf' }),
    ]);
    await render();

    http.expectNone('/api/v1/documents');
    expect(row().textContent).toContain("isn't a PDF");
  });

  // --- queue -----------------------------------------------------------------------------

  it('uploads one file at a time, in order', async () => {
    page.addFiles([pdf('first.pdf'), pdf('second.pdf')]);
    const first = await pendingUpload(); // expectOne: fails if both were sent at once
    expect((first.request.body.get('file') as File).name).toBe('first.pdf');
    await render();
    expect(row(1).textContent).toContain('Waiting');

    first.flush(W2_DONE, { status: 201, statusText: 'Created' });
    const second = await pendingUpload();
    expect((second.request.body.get('file') as File).name).toBe('second.pdf');
    second.flush(W2_DONE, { status: 201, statusText: 'Created' });
  });

  it('accepts files chosen with the file input', async () => {
    const input = el.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [pdf('picked.pdf')] });
    input.dispatchEvent(new Event('change'));

    const req = await pendingUpload();
    expect((req.request.body.get('file') as File).name).toBe('picked.pdf');
    req.flush(W2_DONE, { status: 201, statusText: 'Created' });
  });

  // --- client-level warnings -------------------------------------------------------------

  function warningBox(): HTMLElement | null {
    return el.querySelector('#client-warnings > div');
  }

  it("shows the client's existing warnings as soon as the page opens", async () => {
    http.expectOne(CLIENT_DOCS).flush(clientDocs([MISFILING]));
    await render();

    expect(warningBox()!.textContent).toContain("Check this client's documents");
    expect(warningBox()!.textContent).toContain('belong to 2 different taxpayers');
  });

  it('shows no warning box when the client has no warnings', async () => {
    http.expectOne(CLIENT_DOCS).flush(clientDocs([]));
    await render();

    expect(warningBox()).toBeNull();
  });

  it('re-checks after each upload, so a misfiled document is flagged right away', async () => {
    http.expectOne(CLIENT_DOCS).flush(clientDocs([])); // on open: one taxpayer, fine
    page.addFiles([pdf('someone-elses-1099.pdf')]);
    (await pendingUpload()).flush(NEC_NEEDS_REVIEW, { status: 201, statusText: 'Created' });

    http.expectOne(CLIENT_DOCS).flush(clientDocs([MISFILING])); // after upload: two taxpayers
    await render();

    expect(warningBox()!.textContent).toContain('filed under the wrong client');
  });

  it('does not re-check after a failed upload (nothing was stored)', async () => {
    http.expectOne(CLIENT_DOCS).flush(clientDocs([]));
    page.addFiles([pdf()]);
    (await pendingUpload()).flush({ detail: 'x' }, { status: 502, statusText: 'Bad Gateway' });
    await render();

    http.expectNone(CLIENT_DOCS);
  });

  it("keeps working if the warnings can't be loaded", async () => {
    http
      .expectOne(CLIENT_DOCS)
      .flush({ detail: 'down' }, { status: 503, statusText: 'Unavailable' });
    page.addFiles([pdf()]);
    (await pendingUpload()).flush(W2_DONE, { status: 201, statusText: 'Created' });
    await render();

    expect(row().dataset['status']).toBe('done');
    expect(warningBox()).toBeNull();
  });

  it('announces warnings that appear later to screen readers', () => {
    expect(el.querySelector('#client-warnings')!.getAttribute('aria-live')).toBe('polite');
  });

  // --- accessibility ---------------------------------------------------------------------

  it('is usable by keyboard: a focusable, labelled file input with a hint', () => {
    const input = el.querySelector<HTMLInputElement>('input[type="file"]')!;
    expect(input.disabled).toBe(false);
    expect(input.tabIndex).toBeGreaterThanOrEqual(0);
    expect(input.multiple).toBe(true);

    const label = el.querySelector<HTMLLabelElement>(`label[for="${input.id}"]`)!;
    expect(label.textContent).toContain('choose files');
    expect(el.querySelector(`#${input.getAttribute('aria-describedby')}`)!.textContent).toContain(
      'up to 5 MB',
    );

    input.focus();
    expect(document.activeElement).toBe(input);
  });

  it('announces status changes to screen readers', async () => {
    page.addFiles([pdf()]);
    (await pendingUpload()).flush(W2_DONE, { status: 201, statusText: 'Created' });
    await render();

    expect(el.querySelector('ul[aria-live="polite"]')).not.toBeNull();
  });
});
