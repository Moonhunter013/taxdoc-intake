import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CLIENT02_PLAN } from '../testing/plan-fixtures';
import { PlanningApi } from './planning-api';

describe('PlanningApi', () => {
  let api: PlanningApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PlanningApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a plan with clientRef and taxYear in camelCase, as planning expects', () => {
    api.createPlan('client-02', 2026).subscribe();
    const req = http.expectOne('/api/v1/plans');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ clientRef: 'client-02', taxYear: 2026 });
    req.flush(CLIENT02_PLAN);
  });

  it('keeps money as the decimal strings planning sends', () => {
    let total: unknown;
    api.getPlan(CLIENT02_PLAN.id).subscribe((p) => (total = p.totalEstimatedSavings));
    http.expectOne(`/api/v1/plans/${CLIENT02_PLAN.id}`).flush(CLIENT02_PLAN);
    expect(total).toBe('13316.67');
  });
});
