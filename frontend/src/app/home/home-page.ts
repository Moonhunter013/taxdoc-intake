import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

/** Same rule as extraction (ADR-0006): letters, digits, '-' and '_', starting with a letter or digit. */
export const CLIENT_REF_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/;

/** Start page: pick a client by reference and go to their upload page. */
@Component({
  selector: 'app-home-page',
  template: `
    <h2 class="text-xl font-semibold text-slate-900">Open a client</h2>
    <form class="mt-4 flex flex-wrap items-end gap-3" (submit)="open($event)" novalidate>
      <div class="flex flex-col">
        <label for="client-ref" class="text-sm font-medium text-slate-900">Client reference</label>
        <input
          id="client-ref"
          name="clientRef"
          class="mt-1 rounded border border-slate-300 px-3 py-2 font-mono"
          placeholder="client-02"
          autocomplete="off"
          [attr.aria-invalid]="error() ? 'true' : null"
          [attr.aria-describedby]="error() ? 'client-ref-error' : null"
          [value]="clientRef()"
          (input)="clientRef.set($any($event.target).value)"
        />
      </div>
      <button
        type="submit"
        class="rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800"
      >
        Upload documents
      </button>
    </form>
    @if (error()) {
      <p id="client-ref-error" class="mt-2 text-sm text-red-800" role="alert">{{ error() }}</p>
    }
  `,
})
export class HomePage {
  private readonly router = inject(Router);

  protected readonly clientRef = signal('');
  protected readonly error = signal<string | null>(null);

  protected open(event: Event): void {
    event.preventDefault();
    const ref = this.clientRef().trim();
    if (!CLIENT_REF_PATTERN.test(ref)) {
      this.error.set(
        "Use letters, digits, '-' or '_' (up to 64), starting with a letter or digit, e.g. client-02.",
      );
      return;
    }
    this.error.set(null);
    void this.router.navigate(['/clients', ref, 'upload']);
  }
}
