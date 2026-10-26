import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** planning-service's strategy outcomes (ADR-0007), most actionable first. */
export type Outcome = 'RECOMMENDED' | 'MARGINAL' | 'NOT_APPLICABLE';

export interface PlanResult {
  code: string;
  title: string;
  outcome: Outcome;
  /** Decimal string, e.g. "6656.67"; "0.00" when not applicable. */
  estimatedSavings: string;
  explanation: string;
  assumptions: string[];
}

/**
 * A plan as planning-service returns it (PlanResponse). Money is a decimal string so it is never
 * parsed as a floating-point number; format it with formatMoney().
 */
export interface Plan {
  id: string;
  clientRef: string;
  taxYear: number;
  provisional: boolean;
  provisionalNotice: string | null;
  documentsNeedingReview: string[];
  /** Sum of RECOMMENDED results only. */
  totalEstimatedSavings: string;
  /** Sorted by planning: recommended, marginal, not applicable; larger savings first. */
  results: PlanResult[];
  warnings: string[];
  createdAt: string;
  disclaimer: string;
}

/** Talks to planning-service through the dev proxy (/api/v1/plans -> :8080). */
@Injectable({ providedIn: 'root' })
export class PlanningApi {
  private readonly http = inject(HttpClient);

  /** Runs every strategy for the client and stores the result as a new plan. */
  createPlan(clientRef: string, taxYear: number): Observable<Plan> {
    return this.http.post<Plan>('/api/v1/plans', { clientRef, taxYear });
  }

  /** A stored plan, exactly as it was generated. */
  getPlan(id: string): Observable<Plan> {
    return this.http.get<Plan>(`/api/v1/plans/${encodeURIComponent(id)}`);
  }
}

/**
 * planning-service answers errors with RFC 9457 problem details ({title, detail, ...}); turn
 * them into a message an advisor can act on.
 */
export function describePlanError(error: HttpErrorResponse): string {
  const problem = (error.error ?? {}) as Record<string, unknown>;
  const title = problem['title'];
  switch (error.status) {
    case 0:
      return "Can't reach the planning service. Check your connection and that the services are running.";
    case 404:
      return "This plan doesn't exist. Generate a new one.";
    case 422:
      if (title === 'No documents for client') {
        return 'There are no documents for this client yet. Upload their W-2s and 1099-NECs first.';
      }
      if (title === 'Unsupported tax year') {
        const years = problem['supportedYears'];
        return Array.isArray(years) && years.length > 0
          ? `That tax year isn't supported. Supported years: ${years.join(', ')}.`
          : "That tax year isn't supported.";
      }
      return typeof problem['detail'] === 'string'
        ? problem['detail']
        : "The plan couldn't be generated.";
    case 503:
      return 'The document service is unavailable right now, so no plan could be generated. Please try again in a minute.';
    default:
      return `Something went wrong (HTTP ${error.status}). Please try again.`;
  }
}
