import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ExtractionApi } from '../api/extraction-api';
import { Plan, PlanResult, PlanningApi, describePlanError } from '../api/planning-api';
import { formatMoney } from '../shared/money';

/** Tax years planning-service is configured for (tax-years.yml). It answers 422 for any other. */
export const TAX_YEARS = [2026];

/**
 * /clients/:clientRef/plan, and ?id=<plan id> for a stored plan.
 *
 * Plans are only generated when the advisor asks: every POST stores a new plan, so opening the
 * page must not create one. After generating, the plan id goes into the URL (replacing it), so a
 * reload or a shared link shows the same plan via GET instead of generating another.
 */
@Component({
  selector: 'app-plan-page',
  imports: [RouterLink],
  templateUrl: './plan-page.html',
})
export class PlanPage {
  private readonly planning = inject(PlanningApi);
  private readonly extraction = inject(ExtractionApi);
  private readonly router = inject(Router);

  readonly clientRef = input.required<string>();
  /** Query parameter ?id= (withComponentInputBinding). */
  readonly id = input<string | undefined>();

  protected readonly taxYears = TAX_YEARS;
  protected readonly taxYear = signal(TAX_YEARS[0]);
  protected readonly plan = signal<Plan | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly noDocuments = signal(false);
  /** Labels for the provisional banner's links, e.g. "1099-NEC from Coastal Ferry Authority Co". */
  protected readonly documentLabels = signal<Record<string, string>>({});

  protected readonly recommended = computed(() => this.byOutcome('RECOMMENDED'));
  protected readonly marginal = computed(() => this.byOutcome('MARGINAL'));
  protected readonly notApplicable = computed(() => this.byOutcome('NOT_APPLICABLE'));

  protected readonly money = formatMoney;

  constructor() {
    // Load a stored plan when the URL names one we don't already have (e.g. after a reload).
    effect(() => {
      const id = this.id();
      if (id && this.plan()?.id !== id) {
        untracked(() => this.load(id));
      }
    });
  }

  protected generate(event?: Event): void {
    event?.preventDefault();
    this.start();
    this.planning.createPlan(this.clientRef(), this.taxYear()).subscribe({
      next: (plan) => {
        this.show(plan);
        // Same page, new URL: the effect sees the plan it already has and doesn't reload it.
        void this.router.navigate([], { queryParams: { id: plan.id }, replaceUrl: true });
      },
      error: (error: HttpErrorResponse) => this.fail(error),
    });
  }

  protected reviewLabel(documentId: string, index: number): string {
    return this.documentLabels()[documentId] ?? `Document ${index + 1}`;
  }

  private load(id: string): void {
    this.start();
    this.planning.getPlan(id).subscribe({
      next: (plan) => this.show(plan),
      error: (error: HttpErrorResponse) => this.fail(error),
    });
  }

  private start(): void {
    this.loading.set(true);
    this.error.set(null);
    this.noDocuments.set(false);
  }

  private show(plan: Plan): void {
    this.plan.set(plan);
    this.taxYear.set(plan.taxYear);
    this.loading.set(false);
    if (plan.documentsNeedingReview.length > 0) {
      this.loadDocumentLabels();
    }
  }

  private fail(error: HttpErrorResponse): void {
    this.loading.set(false);
    this.plan.set(null);
    this.error.set(describePlanError(error));
    this.noDocuments.set(
      error.status === 422 &&
        (error.error as { title?: string } | null)?.title === 'No documents for client',
    );
  }

  /** Best effort: if extraction can't be reached, the links fall back to "Document 1, 2...". */
  private loadDocumentLabels(): void {
    this.extraction.clientDocuments(this.clientRef()).subscribe({
      next: (client) => {
        const labels: Record<string, string> = {};
        for (const doc of client.documents) {
          const from = doc.document['employer_name'] ?? doc.document['payer_name'];
          labels[doc.document_id] =
            typeof from === 'string' ? `${doc.form_type} from ${from}` : doc.form_type;
        }
        this.documentLabels.set(labels);
      },
      error: () => undefined,
    });
  }

  private byOutcome(outcome: PlanResult['outcome']): PlanResult[] {
    return this.plan()?.results.filter((r) => r.outcome === outcome) ?? [];
  }
}
