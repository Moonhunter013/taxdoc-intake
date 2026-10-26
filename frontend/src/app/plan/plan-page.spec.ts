import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Plan } from '../api/planning-api';
import {
  CLIENT02_PLAN,
  CLIENT02_PROVISIONAL,
  CLIENT04_PLAN,
  CLIENT07_PLAN,
  NEEDS_REVIEW_DOC,
} from '../testing/plan-fixtures';
import { PlanPage } from './plan-page';

const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

describe('PlanPage', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;

  /** Real routing, so clientRef and ?id= are bound exactly as in the app. */
  async function open(url: string): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'clients/:clientRef/plan', component: PlanPage }],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, PlanPage);
    return harness.routeNativeElement as HTMLElement;
  }

  async function render(): Promise<void> {
    await settle();
    harness.detectChanges();
    await settle();
  }

  async function generate(el: HTMLElement): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await render();
  }

  afterEach(() => http.verify());

  const text = (el: HTMLElement, selector: string) =>
    el.querySelector(selector)?.textContent?.trim() ?? '';
  const codes = (el: HTMLElement, section: string) =>
    [...el.querySelectorAll(`section[aria-labelledby="${section}"] [data-code]`)].map((a) =>
      a.getAttribute('data-code'),
    );

  // --- generating ------------------------------------------------------------------------

  it('does not generate a plan just by opening the page (every POST stores a new plan)', async () => {
    const el = await open('/clients/client-02/plan');
    await render();
    http.expectNone('/api/v1/plans');
    expect(text(el, 'button[type="submit"]')).toBe('Generate plan');
  });

  it("generates client-02's plan: S-corp and Solo 401(k), with the total of the recommended results", async () => {
    const el = await open('/clients/client-02/plan');
    await generate(el);

    const req = http.expectOne('/api/v1/plans');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ clientRef: 'client-02', taxYear: 2026 });
    req.flush(CLIENT02_PLAN, { status: 201, statusText: 'Created' });
    await render();

    expect(text(el, '[data-test="total"]')).toBe('$13,316.67');
    expect(codes(el, 'recommended-heading')).toEqual(['SOLO_401K', 'S_CORP_ELECTION']);
    const solo = el.querySelector('[data-code="SOLO_401K"]')!;
    expect(solo.textContent).toContain('$6,660.00 a year');
    expect(el.querySelector('[data-code="S_CORP_ELECTION"]')!.textContent).toContain(
      '$6,656.67 a year',
    );
    expect(text(el, '[data-test="disclaimer"]')).toContain('not tax advice');
  });

  it('puts the new plan in the URL without loading it again', async () => {
    const el = await open('/clients/client-02/plan');
    await generate(el);
    http.expectOne('/api/v1/plans').flush(CLIENT02_PLAN, { status: 201, statusText: 'Created' });
    await render();

    expect(router.url).toBe(`/clients/client-02/plan?id=${CLIENT02_PLAN.id}`);
    http.expectNone(`/api/v1/plans/${CLIENT02_PLAN.id}`);
  });

  // --- stored plans ----------------------------------------------------------------------

  it('loads a stored plan from ?id= (a reload or shared link), without generating a new one', async () => {
    const el = await open(`/clients/client-07/plan?id=${CLIENT07_PLAN.id}`);
    const req = http.expectOne(`/api/v1/plans/${CLIENT07_PLAN.id}`);
    expect(req.request.method).toBe('GET');
    req.flush(CLIENT07_PLAN);
    await render();

    expect(text(el, '[data-test="total"]')).toBe('$3,135.00');
  });

  it('shows a marginal result with its amount, but outside the total', async () => {
    const el = await open(`/clients/client-07/plan?id=${CLIENT07_PLAN.id}`);
    http.expectOne(`/api/v1/plans/${CLIENT07_PLAN.id}`).flush(CLIENT07_PLAN);
    await render();

    expect(codes(el, 'marginal-heading')).toEqual(['S_CORP_ELECTION']);
    const marginal = el.querySelector('section[aria-labelledby="marginal-heading"]')!;
    expect(marginal.textContent).toContain('$2,202.07 a year');
    expect(marginal.textContent).toContain('Not counted in the total');
    expect(codes(el, 'recommended-heading')).toEqual(['SOLO_401K']);
  });

  it('shows an empty state for a client with nothing to recommend (control client-04)', async () => {
    const el = await open(`/clients/client-04/plan?id=${CLIENT04_PLAN.id}`);
    http.expectOne(`/api/v1/plans/${CLIENT04_PLAN.id}`).flush(CLIENT04_PLAN);
    await render();

    expect(text(el, '[data-test="empty"]')).toContain('No recommended strategies for this client');
    expect(text(el, '[data-test="total"]')).toBe('$0.00');
    expect(codes(el, 'marginal-heading')).toEqual(['WORKPLACE_401K', 'HSA']);
  });

  it('keeps not-applicable strategies in a collapsed list', async () => {
    const el = await open(`/clients/client-02/plan?id=${CLIENT02_PLAN.id}`);
    http.expectOne(`/api/v1/plans/${CLIENT02_PLAN.id}`).flush(CLIENT02_PLAN);
    await render();

    const list = el.querySelector<HTMLDetailsElement>('[data-test="not-applicable"]')!;
    expect(list.open).toBe(false);
    expect(list.querySelector('summary')!.textContent).toContain('Not applicable (3)');
  });

  // --- provisional plans -----------------------------------------------------------------

  it('leads with a provisional banner linking to each document that needs review', async () => {
    const el = await open(`/clients/client-02/plan?id=${CLIENT02_PLAN.id}`);
    http.expectOne(`/api/v1/plans/${CLIENT02_PLAN.id}`).flush(CLIENT02_PROVISIONAL);
    await render();
    http.expectOne('/api/v1/clients/client-02/documents').flush({
      client_ref: 'client-02',
      taxpayer_groups: [],
      warnings: [],
      documents: [
        {
          document_id: NEEDS_REVIEW_DOC,
          form_type: '1099-NEC',
          document: { payer_name: 'Coastal Ferry Authority Co' },
        },
      ],
    });
    await render();

    const banner = el.querySelector('[data-test="provisional"]')!;
    expect(banner.textContent).toContain('Provisional plan');
    expect(banner.textContent).toContain('still need review');
    const link = banner.querySelector('a')!;
    expect(link.textContent).toContain('Review 1099-NEC from Coastal Ferry Authority Co');
    expect(link.getAttribute('href')).toBe(`/clients/client-02/documents/${NEEDS_REVIEW_DOC}`);
    // Before the total in reading order, so it's seen first.
    const total = el.querySelector('[data-test="total"]')!;
    expect(banner.compareDocumentPosition(total) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("still links to the documents if their names can't be loaded", async () => {
    const el = await open(`/clients/client-02/plan?id=${CLIENT02_PLAN.id}`);
    http.expectOne(`/api/v1/plans/${CLIENT02_PLAN.id}`).flush(CLIENT02_PROVISIONAL);
    await render();
    http
      .expectOne('/api/v1/clients/client-02/documents')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    await render();

    expect(el.querySelector('[data-test="provisional"] a')!.textContent).toContain(
      'Review Document 1',
    );
  });

  // --- errors ----------------------------------------------------------------------------

  async function generateWithError(status: number, body: object): Promise<HTMLElement> {
    const el = await open('/clients/client-02/plan');
    await generate(el);
    http.expectOne('/api/v1/plans').flush(body, { status, statusText: 'Error' });
    await render();
    return el;
  }

  it('says there are no documents yet, and links to the upload page', async () => {
    const el = await generateWithError(422, {
      status: 422,
      title: 'No documents for client',
      detail: '...',
    });
    expect(text(el, '[role="alert"]')).toContain('no documents for this client yet');
    expect(el.querySelector('[role="alert"] a')!.getAttribute('href')).toBe(
      '/clients/client-02/upload',
    );
  });

  it('lists the supported years for an unsupported tax year', async () => {
    const el = await generateWithError(422, {
      title: 'Unsupported tax year',
      supportedYears: [2026],
    });
    expect(text(el, '[role="alert"]')).toContain('Supported years: 2026');
  });

  it('explains that extraction is unavailable (503)', async () => {
    const el = await generateWithError(503, {
      title: 'Extraction service unavailable',
      reason: 'TIMEOUT',
    });
    expect(text(el, '[role="alert"]')).toContain('document service is unavailable');
  });

  it("says a plan link doesn't exist (404)", async () => {
    const el = await open('/clients/client-02/plan?id=nope');
    http
      .expectOne('/api/v1/plans/nope')
      .flush({ title: 'Plan not found' }, { status: 404, statusText: 'Not Found' });
    await render();
    expect(text(el, '[role="alert"]')).toContain("This plan doesn't exist");
  });

  it("explains a network failure (can't reach the server)", async () => {
    const el = await open('/clients/client-02/plan');
    await generate(el);
    http.expectOne('/api/v1/plans').error(new ProgressEvent('error'));
    await render();
    expect(text(el, '[role="alert"]')).toContain("Can't reach the planning service");
  });

  // --- accessibility ---------------------------------------------------------------------

  it('has a labelled tax-year picker and keyboard-reachable assumptions', async () => {
    const el = await open(`/clients/client-02/plan?id=${CLIENT02_PLAN.id}`);
    http.expectOne(`/api/v1/plans/${CLIENT02_PLAN.id}`).flush(CLIENT02_PLAN as Plan);
    await render();

    expect(el.querySelector('label[for="tax-year"]')!.textContent).toContain('Tax year');
    const summary = el.querySelector<HTMLElement>('[data-code="SOLO_401K"] details summary')!;
    expect(summary.textContent).toContain('Assumptions (2)');
    summary.focus();
    expect(document.activeElement).toBe(summary);
  });
});
