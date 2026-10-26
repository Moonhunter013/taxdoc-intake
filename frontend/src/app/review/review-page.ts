import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import {
  CorrectionValue,
  ExtractedDocument,
  ExtractionApi,
  FieldMeta,
} from '../api/extraction-api';
import { formatMoney } from '../shared/money';
import { FieldGroup, FieldSpec, LAYOUT } from './field-layout';

/** Remembered for this browser tab only; there's no login yet (auth comes with deployment). */
export const CORRECTED_BY_KEY = 'taxdoc.correctedBy';

interface Box12Row {
  code: string;
  amount: string;
}

/**
 * /clients/:clientRef/documents/:id: check what Textract read, box by box, and correct it.
 * Every save round-trips extraction, which re-runs the review rules; the returned document
 * replaces the screen, so flags appear or clear on any field, not only the one edited.
 */
@Component({
  selector: 'app-review-page',
  imports: [RouterLink],
  templateUrl: './review-page.html',
})
export class ReviewPage implements OnInit {
  private readonly api = inject(ExtractionApi);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly clientRef = input.required<string>();
  readonly id = input.required<string>();

  protected readonly doc = signal<ExtractedDocument | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly correctedBy = signal(readCorrectedBy());
  protected readonly nameMissing = signal(false);

  /** The field being edited, its draft value and any error from the last save. */
  protected readonly editing = signal<string | null>(null);
  protected readonly draft = signal<string>('');
  protected readonly draftBool = signal(false);
  protected readonly draftRows = signal<Box12Row[]>([]);
  protected readonly fieldError = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly groups = computed<FieldGroup[]>(() => {
    const doc = this.doc();
    return doc ? LAYOUT[doc.form_type] : [];
  });

  ngOnInit(): void {
    this.api.getDocument(this.id()).subscribe({
      next: (doc) => this.doc.set(doc),
      error: (error: HttpErrorResponse) =>
        this.loadError.set(
          error.status === 404
            ? 'This document was not found. It may have been deleted.'
            : "The document couldn't be loaded. Check that the services are running and try again.",
        ),
    });
  }

  // --- display ---------------------------------------------------------------------------

  protected meta(name: string): FieldMeta | undefined {
    return this.doc()?.fields[name];
  }

  protected display(spec: FieldSpec): string {
    const value = this.doc()?.document[spec.name];
    switch (spec.kind) {
      case 'money':
        return formatMoney(value);
      case 'bool':
        return value ? 'Yes' : 'No';
      case 'box12':
        return Array.isArray(value) && value.length > 0
          ? value.map((e) => `${e.code} ${formatMoney(e.amount)}`).join(', ')
          : 'None';
      default:
        // TIN values arrive already masked from the API; nothing here can unmask them.
        return value == null || value === '' ? '—' : String(value);
    }
  }

  protected confidenceText(meta: FieldMeta): string {
    return meta.source_label === 'manual correction'
      ? 'Entered manually'
      : `Textract confidence ${meta.confidence.toFixed(1)}%`;
  }

  protected correctedText(meta: FieldMeta): string | null {
    if (!meta.corrected_by) {
      return null;
    }
    const when = meta.corrected_at ? new Date(meta.corrected_at).toLocaleString() : '';
    return `Corrected by ${meta.corrected_by}${when ? `, ${when}` : ''}`;
  }

  // --- editing ---------------------------------------------------------------------------

  protected setCorrectedBy(name: string): void {
    this.correctedBy.set(name);
    this.nameMissing.set(false);
    try {
      sessionStorage.setItem(CORRECTED_BY_KEY, name);
    } catch {
      // Storage can be unavailable (private mode, blocked); the name still works for this page.
    }
  }

  protected startEdit(spec: FieldSpec): void {
    const value = this.doc()?.document[spec.name];
    this.fieldError.set(null);
    this.editing.set(spec.name);
    if (spec.kind === 'bool') {
      this.draftBool.set(Boolean(value));
    } else if (spec.kind === 'box12') {
      const rows = Array.isArray(value) ? value : [];
      this.draftRows.set(rows.map((e) => ({ code: String(e.code), amount: String(e.amount) })));
    } else {
      // A TIN starts empty: the full number isn't known (only the mask), and is never pre-filled.
      this.draft.set(spec.kind === 'tin' || value == null ? '' : String(value));
    }
    // Keyboard users land in the editor (the autofocus attribute doesn't work on inserted elements).
    this.focusLater(`[data-field="${spec.name}"] form input`);
  }

  protected cancelEdit(): void {
    const field = this.editing();
    this.editing.set(null);
    this.draft.set(''); // also drops a typed SSN from memory
    this.fieldError.set(null);
    if (field) {
      this.focusLater(`[data-field="${field}"] button`); // back to the field's Edit button
    }
  }

  private focusLater(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }

  protected setRow(index: number, part: keyof Box12Row, value: string): void {
    this.draftRows.update((rows) =>
      rows.map((r, i) => (i === index ? { ...r, [part]: value } : r)),
    );
  }

  protected addRow(): void {
    this.draftRows.update((rows) => [...rows, { code: '', amount: '' }]);
  }

  protected removeRow(index: number): void {
    this.draftRows.update((rows) => rows.filter((_, i) => i !== index));
  }

  protected save(spec: FieldSpec, event?: Event): void {
    event?.preventDefault();
    const doc = this.doc();
    const who = this.correctedBy().trim();
    if (!doc) {
      return;
    }
    if (!who) {
      this.nameMissing.set(true);
      this.fieldError.set(
        'Enter your name at the top of the page first; it is recorded with each correction.',
      );
      return;
    }
    this.saving.set(true);
    this.api.correctField(doc.document_id, spec.name, this.valueFor(spec), who).subscribe({
      next: (updated) => {
        this.doc.set(updated);
        this.saving.set(false);
        this.cancelEdit();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.fieldError.set(describeCorrectionError(error));
      },
    });
  }

  private valueFor(spec: FieldSpec): CorrectionValue {
    switch (spec.kind) {
      case 'bool':
        return this.draftBool();
      case 'box12':
        return this.draftRows().map((r) => ({
          code: r.code.trim().toUpperCase(),
          amount: r.amount.trim(),
        }));
      default: {
        const text = this.draft().trim().replace(/^\$/, '').replaceAll(',', '');
        return text === '' && spec.optional
          ? null
          : spec.kind === 'money'
            ? text
            : this.draft().trim();
      }
    }
  }
}

function readCorrectedBy(): string {
  try {
    return sessionStorage.getItem(CORRECTED_BY_KEY) ?? '';
  } catch {
    return '';
  }
}

/** extraction's 422 says which rule failed ("Invalid value for box1_wages: ..."), never the value. */
export function describeCorrectionError(error: HttpErrorResponse): string {
  const detail = (error.error as { detail?: unknown } | null)?.detail;
  if (error.status === 422 && typeof detail === 'string') {
    return detail;
  }
  if (error.status === 422 && Array.isArray(detail)) {
    return (
      'The value was rejected: ' +
      detail
        .map((d) => d?.msg)
        .filter(Boolean)
        .join('; ')
    );
  }
  if (error.status === 404) {
    return 'This document or field no longer exists.';
  }
  if (error.status === 0) {
    return "Can't reach the document service. Check that the services are running.";
  }
  return `Saving failed (HTTP ${error.status}). Please try again.`;
}
