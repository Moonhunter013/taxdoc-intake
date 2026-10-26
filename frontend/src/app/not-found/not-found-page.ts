import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  template: `
    <h2 class="text-xl font-semibold text-slate-900">Page not found</h2>
    <p class="mt-2 text-slate-700">
      This page doesn't exist (yet).
      <a routerLink="/" class="rounded text-blue-700 underline">Go to the start page</a>.
    </p>
  `,
})
export class NotFoundPage {}
